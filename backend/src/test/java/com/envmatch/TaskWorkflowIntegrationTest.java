package com.envmatch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.envmatch.model.AIModel;
import com.envmatch.model.ModelCallLog;
import com.envmatch.model.Task;
import com.envmatch.model.TaskStatus;
import com.envmatch.mapper.AIModelMapper;
import com.envmatch.mapper.ModelCallLogMapper;
import com.envmatch.mapper.TaskMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskWorkflowIntegrationTest {
    private static final Path TEST_ROOT = createTempRoot();
    private static final List<String> WEBHOOK_BODIES = new CopyOnWriteArrayList<>();
    private static HttpServer mockServer;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    AIModelMapper modelMapper;

    @Autowired
    ModelCallLogMapper logMapper;

    @Autowired
    TaskMapper taskMapper;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        startMockServer();

        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "5432");
        String user = System.getenv().getOrDefault("DB_USERNAME", "postgres");
        String pass = System.getenv().getOrDefault("DB_PASSWORD", "postgres");

        // 1. Connect to administrative database 'postgres' to try creating 'envmatch_test' database
        String adminUrl = String.format("jdbc:postgresql://%s:%s/postgres", host, port);
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(adminUrl, user, pass)) {
            try (java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DATABASE envmatch_test");
                System.out.println("Created test database envmatch_test");
            }
        } catch (Exception e) {
            // Ignore if database already exists or other creation error, just proceed
            System.out.println("Test database envmatch_test might already exist: " + e.getMessage());
        }

        // 2. Set database connection URL properties for Spring Boot to use envmatch_test
        String testUrl = String.format("jdbc:postgresql://%s:%s/envmatch_test", host, port);
        registry.add("spring.datasource.url", () -> testUrl);

        // 3. Drop existing tables in envmatch_test to ensure a clean PostgreSQL schema initialization for testing
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(testUrl, user, pass)) {
            try (java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS task_results CASCADE");
                stmt.execute("DROP TABLE IF EXISTS tasks CASCADE");
                stmt.execute("DROP TABLE IF EXISTS model_call_logs CASCADE");
                stmt.execute("DROP TABLE IF EXISTS prompt_templates CASCADE");
                stmt.execute("DROP TABLE IF EXISTS ai_models CASCADE");
            }
        } catch (Exception e) {
            System.err.println("Could not drop tables in envmatch_test before test: " + e.getMessage());
        }

        registry.add("envmatch.storage-dir", () -> TEST_ROOT.resolve("storage").toString());
        registry.add("envmatch.webhook-url", () -> "http://127.0.0.1:" + mockServer.getAddress().getPort() + "/webhook");
    }

    @AfterAll
    static void shutdown() {
        if (mockServer != null) mockServer.stop(0);
    }

    @Test
    void createsTaskCallsModelLogsWebhookAndDeletesTask() throws Exception {
        String identifier = "mock-vlm-" + UUID.randomUUID();
        AIModel model = new AIModel();
        model.setName("Mock VLM");
        model.setIdentifier(identifier);
        model.setProvider("OpenAI");
        model.setApiKey("test-key");
        model.setBaseUrl("http://127.0.0.1:" + mockServer.getAddress().getPort() + "/v1");
        model.setCapabilities(mapper.readTree("[\"text\",\"image\"]"));
        model.setIsDefault("false");
        modelMapper.insert(model);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("task_name", "integration-task");
        body.add("model_id", identifier);
        body.add("prompt", "Return JSON only");
        body.add("preprocess_options", "{\"recognition_mode\":\"image\",\"sampling_type\":\"fixed\",\"sampling_fps\":1,\"resolution\":true,\"resolution_val\":120}");
        body.add("video_a", imageResource("a.png", Color.BLUE));
        body.add("video_b", imageResource("b.png", Color.GREEN));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<JsonNode> create = restTemplate.postForEntity(baseUrl() + "/tasks/", new HttpEntity<>(body, headers), JsonNode.class);

        assertThat(create.getStatusCode().is2xxSuccessful()).isTrue();
        String taskId = create.getBody().path("task_id").asText();
        assertThat(taskId).isNotBlank();

        JsonNode detail = waitForTerminalTask(taskId);
        assertThat(detail.path("task").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(detail.path("task").path("similarity_score").asDouble()).isEqualTo(87.0);
        assertThat(detail.path("result").path("summary").asText()).isEqualTo("mock analysis completed");
        assertThat(detail.path("result").path("key_frames_a")).hasSize(1);
        assertThat(detail.path("result").path("key_frames_b")).hasSize(1);

        JsonNode logs = restTemplate.getForObject(baseUrl() + "/model-logs/?search=" + identifier + "&skip=0&limit=5", JsonNode.class);
        assertThat(logs.path("total").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(logs.path("logs").get(0).path("status_code").asText()).isEqualTo("200");

        long count = logMapper.selectCount(new LambdaQueryWrapper<ModelCallLog>()
                .like(ModelCallLog::getTaskName, identifier)
                .or().like(ModelCallLog::getTaskId, identifier)
                .or().like(ModelCallLog::getModelId, identifier));
        assertThat(count).isGreaterThanOrEqualTo(1);

        assertThat(WEBHOOK_BODIES).anySatisfy(webhook -> {
            assertThat(webhook).contains(taskId);
            assertThat(webhook).contains("COMPLETED");
        });

        restTemplate.delete(baseUrl() + "/tasks/" + taskId);
        ResponseEntity<String> afterDelete = restTemplate.exchange(baseUrl() + "/tasks/" + taskId, HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertThat(afterDelete.getStatusCode().value()).isEqualTo(404);
        assertThat(taskMapper.selectById(taskId)).isNull();
    }

    @Test
    void supportsArbitraryLogOffsets() {
        String marker = "offset-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            ModelCallLog log = new ModelCallLog();
            log.setTaskId(marker);
            log.setTaskName(marker);
            log.setModelId("model-" + i);
            log.setStartedAt(LocalDateTime.now().plusMinutes(i));
            logMapper.insert(log);
        }

        JsonNode response = restTemplate.getForObject(
                baseUrl() + "/model-logs/?search=" + marker + "&skip=1&limit=1",
                JsonNode.class
        );

        assertThat(response.path("total").asInt()).isEqualTo(3);
        assertThat(response.path("logs")).hasSize(1);
        assertThat(response.path("logs").get(0).path("model_id").asText()).isEqualTo("model-1");
    }

    @Test
    void sortsMixedLegacyAndJavaTimestampsChronologically() {
        String marker = "mixed-time-" + UUID.randomUUID();
        String legacyTaskId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO tasks (id, task_name, status, created_at, updated_at)
                VALUES (?, ?, 'PENDING', '2000-01-01 00:00:00.000000', '2000-01-01 00:00:00.000000')
                """, legacyTaskId, marker + "-legacy-task");

        Task currentTask = new Task();
        currentTask.setTaskName(marker + "-current-task");
        currentTask.setStatus(TaskStatus.PENDING);
        taskMapper.insert(currentTask);

        ModelCallLog legacyLog = new ModelCallLog();
        legacyLog.setTaskId(marker + "-legacy-log");
        legacyLog.setTaskName(marker);
        legacyLog.setModelId(marker);
        legacyLog.setStartedAt(LocalDateTime.of(2000, 1, 1, 0, 0));
        logMapper.insert(legacyLog);
        jdbcTemplate.update(
                "UPDATE model_call_logs SET started_at = '2000-01-01 00:00:00.000000' WHERE id = ?",
                legacyLog.getId()
        );

        ModelCallLog currentLog = new ModelCallLog();
        currentLog.setTaskId(marker + "-current-log");
        currentLog.setTaskName(marker);
        currentLog.setModelId(marker);
        currentLog.setStartedAt(LocalDateTime.now());
        logMapper.insert(currentLog);

        JsonNode tasks = restTemplate.getForObject(
                baseUrl() + "/tasks/?status=PROCESSING&skip=0&limit=500",
                JsonNode.class
        );
        JsonNode logs = restTemplate.getForObject(
                baseUrl() + "/model-logs/?search=" + marker + "&skip=0&limit=10",
                JsonNode.class
        );

        assertThat(tasks.path("tasks").get(0).path("id").asText()).isEqualTo(currentTask.getId());
        assertThat(logs.path("logs").get(0).path("id").asLong()).isEqualTo(currentLog.getId());

        logMapper.deleteById(currentLog.getId());
        logMapper.deleteById(legacyLog.getId());
        taskMapper.deleteById(currentTask.getId());
        jdbcTemplate.update("DELETE FROM tasks WHERE id = ?", legacyTaskId);
    }

    @Test
    void excludesNullSimilarityFromDistribution() {
        JsonNode before = restTemplate.getForObject(baseUrl() + "/dashboard-stats", JsonNode.class);
        int lowBefore = before.path("distribution").path("low").asInt();

        Task pending = new Task();
        pending.setTaskName("pending-without-score");
        pending.setStatus(TaskStatus.PENDING);
        taskMapper.insert(pending);

        JsonNode after = restTemplate.getForObject(baseUrl() + "/dashboard-stats", JsonNode.class);
        assertThat(after.path("distribution").path("low").asInt()).isEqualTo(lowBefore);

        taskMapper.deleteById(pending.getId());
    }

    @Test
    void rejectsInvalidModelAndPreprocessRequests() throws Exception {
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> invalidModel = restTemplate.postForEntity(
                baseUrl() + "/models/",
                new HttpEntity<>("{\"name\":\"\",\"identifier\":\"\",\"provider\":\"\"}", jsonHeaders),
                String.class
        );
        assertThat(invalidModel.getStatusCode().value()).isEqualTo(400);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("task_name", "invalid-options");
        body.add("preprocess_options", "{\"sampling_fps\":99}");
        body.add("video_a", imageResource("a.png", Color.BLUE));
        body.add("video_b", imageResource("b.png", Color.GREEN));
        HttpHeaders multipartHeaders = new HttpHeaders();
        multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<String> invalidTask = restTemplate.postForEntity(
                baseUrl() + "/tasks/",
                new HttpEntity<>(body, multipartHeaders),
                String.class
        );
        assertThat(invalidTask.getStatusCode().value()).isEqualTo(400);

        MultiValueMap<String, Object> invalidClipBody = new LinkedMultiValueMap<>();
        invalidClipBody.add("task_name", "invalid-clip");
        invalidClipBody.add("preprocess_options", "{\"clip_start_seconds\":15,\"clip_end_seconds\":15}");
        invalidClipBody.add("video_a", imageResource("a.png", Color.BLUE));
        invalidClipBody.add("video_b", imageResource("b.png", Color.GREEN));
        ResponseEntity<String> invalidClip = restTemplate.postForEntity(
                baseUrl() + "/tasks/",
                new HttpEntity<>(invalidClipBody, multipartHeaders),
                String.class
        );
        assertThat(invalidClip.getStatusCode().value()).isEqualTo(400);
    }

    private JsonNode waitForTerminalTask(String taskId) throws Exception {
        for (int i = 0; i < 20; i++) {
            JsonNode detail = restTemplate.getForObject(baseUrl() + "/tasks/" + taskId, JsonNode.class);
            String status = detail.path("task").path("status").asText();
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) return detail;
            Thread.sleep(500);
        }
        throw new AssertionError("Task did not finish in time");
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static ByteArrayResource imageResource(String filename, Color color) throws IOException {
        byte[] bytes = imageBytes(color);
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private static byte[] imageBytes(Color color) throws IOException {
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static void startMockServer() {
        if (mockServer != null) return;
        try {
            mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            mockServer.createContext("/v1/chat/completions", exchange -> {
                byte[] response = """
                        {"choices":[{"message":{"content":"{\\"similarity_score\\":87,\\"dimension_scores\\":{\\"lighting_weather\\":88,\\"architecture\\":82,\\"facilities\\":79,\\"vegetation\\":91,\\"road_surface\\":84},\\"similar_points\\":[\\"same lighting\\"],\\"difference_points\\":[\\"different color\\"],\\"summary\\":\\"mock analysis completed\\"}"}}],"usage":{"prompt_tokens":1234,"completion_tokens":56}}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(response);
                }
            });
            mockServer.createContext("/webhook", exchange -> {
                WEBHOOK_BODIES.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(response);
                }
            });
            mockServer.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start mock server", e);
        }
    }

    private static Path createTempRoot() {
        try {
            return Files.createTempDirectory("envmatch-test-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create test directory", e);
        }
    }
}
