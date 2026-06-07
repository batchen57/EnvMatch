package com.envmatch.service;

import com.envmatch.mapper.ModelCallLogMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiAnalysisServiceSafetyTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AiAnalysisService service = new AiAnalysisService(
            mapper,
            mock(ModelCallLogMapper.class),
            "storage",
            16 * 1024 * 1024
    );

    @Test
    void redactsVideoAndLargeBase64FromLogs() throws Exception {
        String video = "data:video/mp4;base64," + "A".repeat(2_000_000);
        String largeBase64 = "B".repeat(1_100_000);
        JsonNode payload = mapper.readTree("""
                {
                  "messages": [{
                    "content": [
                      {"type": "video_url", "video_url": {"url": "%s"}},
                      {"type": "text", "text": "keep me"}
                    ]
                  }],
                  "large": "%s"
                }
                """.formatted(video, largeBase64));

        JsonNode sanitized = service.sanitizeForLog(payload);

        assertThat(sanitized.toString()).doesNotContain("A".repeat(1000));
        assertThat(sanitized.toString()).doesNotContain("B".repeat(1000));
        assertThat(sanitized.toString()).contains("redacted video payload");
        assertThat(sanitized.toString()).contains("redacted large-base64 payload");
        assertThat(sanitized.toString()).contains("keep me");
    }

    @Test
    void rejectsInlineMediaWhenAggregateSizeExceedsLimit() throws Exception {
        AiAnalysisService limited = new AiAnalysisService(
                mapper,
                mock(ModelCallLogMapper.class),
                "storage",
                10
        );
        Path first = Files.createTempFile("envmatch-inline-a-", ".mp4");
        Path second = Files.createTempFile("envmatch-inline-b-", ".mp4");
        Files.write(first, new byte[6]);
        Files.write(second, new byte[6]);

        assertThat(limited.canInlineMedia(first.toString(), second.toString())).isFalse();
    }

    @Test
    void frameGridPreservesAspectRatioAtHigherResolution() throws Exception {
        Path wide = imageFile(800, 400, Color.RED);
        Path tall = imageFile(400, 800, Color.BLUE);

        BufferedImage wideGrid = decode(service.frameGridBase64(List.of(wide.toString()), "A"));
        BufferedImage tallGrid = decode(service.frameGridBase64(List.of(tall.toString()), "B"));

        assertThat(wideGrid.getWidth()).isEqualTo(640);
        assertThat(wideGrid.getHeight()).isEqualTo(392);
        assertThat(wideGrid.getRGB(100, 37) & 0xFFFFFF).isLessThan(0x202020);
        assertThat(new Color(wideGrid.getRGB(320, 212)).getRed()).isGreaterThan(200);
        assertThat(tallGrid.getWidth()).isEqualTo(640);
        assertThat(tallGrid.getHeight()).isEqualTo(392);
        assertThat(tallGrid.getRGB(10, 212) & 0xFFFFFF).isLessThan(0x202020);
        assertThat(new Color(tallGrid.getRGB(320, 212)).getBlue()).isGreaterThan(200);
    }

    @Test
    void frameGridSamplesEightFramesAcrossTheWholeClip() throws Exception {
        List<String> frames = List.of(
                imageFile(160, 90, Color.RED).toString(),
                imageFile(160, 90, Color.GREEN).toString(),
                imageFile(160, 90, Color.YELLOW).toString(),
                imageFile(160, 90, Color.CYAN).toString(),
                imageFile(160, 90, Color.MAGENTA).toString(),
                imageFile(160, 90, Color.ORANGE).toString(),
                imageFile(160, 90, Color.PINK).toString(),
                imageFile(160, 90, Color.LIGHT_GRAY).toString(),
                imageFile(160, 90, Color.BLUE).toString()
        );

        BufferedImage grid = decode(service.frameGridBase64(frames, "A"));
        Color firstFrame = new Color(grid.getRGB(320, 212));
        Color lastFrame = new Color(grid.getRGB(960, 1388));

        assertThat(grid.getWidth()).isEqualTo(1280);
        assertThat(grid.getHeight()).isEqualTo(1568);
        assertThat(firstFrame.getRed()).isGreaterThan(200);
        assertThat(lastFrame.getBlue()).isGreaterThan(200);
    }

    @Test
    void openAiImagePayloadContainsSeparateAAndBGrids() throws Exception {
        Path frameA = imageFile(160, 90, Color.RED);
        Path frameB = imageFile(160, 90, Color.BLUE);
        Method method = AiAnalysisService.class.getDeclaredMethod(
                "buildPayload",
                List.class, List.class, String.class, String.class, String.class, String.class,
                boolean.class, boolean.class, String.class, String.class
        );
        method.setAccessible(true);
        Object envelope = method.invoke(
                service,
                List.of(frameA.toString()), List.of(frameB.toString()), "compare", "test-model",
                "OpenAI", "image", false, false, "", ""
        );
        Method payloadAccessor = envelope.getClass().getDeclaredMethod("payload");
        payloadAccessor.setAccessible(true);
        JsonNode payload = (JsonNode) payloadAccessor.invoke(envelope);
        JsonNode content = payload.path("messages").path(0).path("content");

        assertThat(content).hasSize(3);
        assertThat(content.path(0).path("text").asText()).contains("第一张合图", "第二张合图");
        assertThat(content.path(1).path("image_url").path("url").asText()).startsWith("data:image/jpeg;base64,");
        assertThat(content.path(2).path("image_url").path("url").asText()).startsWith("data:image/jpeg;base64,");
    }

    @Test
    void minimaxImagePayloadUsesHybridFormat() throws Exception {
        Path frameA = imageFile(160, 90, Color.RED);
        Path frameB = imageFile(160, 90, Color.BLUE);
        Method method = AiAnalysisService.class.getDeclaredMethod(
                "buildPayload",
                List.class, List.class, String.class, String.class, String.class, String.class,
                boolean.class, boolean.class, String.class, String.class
        );
        method.setAccessible(true);
        Object envelope = method.invoke(
                service,
                List.of(frameA.toString()), List.of(frameB.toString()), "compare", "MiniMax-M3",
                "MiniMax", "image", false, false, "", ""
        );
        Method payloadAccessor = envelope.getClass().getDeclaredMethod("payload");
        payloadAccessor.setAccessible(true);
        JsonNode payload = (JsonNode) payloadAccessor.invoke(envelope);

        JsonNode message = payload.path("messages").path(0);
        assertThat(message.path("content").isTextual()).isTrue();
        assertThat(message.path("content").asText()).contains("第一张合图", "第二张合图");
        assertThat(message.path("images")).hasSize(1);
        assertThat(message.path("images").get(0).asText()).isNotEmpty();
        assertThat(message.path("images").get(0).asText()).doesNotStartWith("data:image");
    }

    private BufferedImage decode(String encoded) throws Exception {
        BufferedImage grid = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
        assertThat(grid).isNotNull();
        return grid;
    }

    private Path imageFile(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        Path path = Files.createTempFile("envmatch-grid-", ".png");
        ImageIO.write(image, "png", path.toFile());
        return path;
    }
}
