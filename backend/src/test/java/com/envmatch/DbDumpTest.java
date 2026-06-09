package com.envmatch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@SpringBootTest
class DbDumpTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void dumpTasksAndLogs() {
        System.out.println("==================================================");
        System.out.println("               ENVMATCH DB DUMP                   ");
        System.out.println("==================================================");

        // 1. Dump Tasks
        List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
                "SELECT * FROM tasks ORDER BY created_at DESC"
        );
        System.out.println("Total tasks found: " + tasks.size());
        for (Map<String, Object> task : tasks) {
            String taskId = (String) task.get("id");
            String taskName = (String) task.get("task_name");
            System.out.println("\n--------------------------------------------------");
            System.out.println("TASK: " + taskName + " (ID: " + taskId + ")");
            System.out.println("Status: " + task.get("status"));
            System.out.println("Similarity Score: " + task.get("similarity_score"));
            System.out.println("Model ID: " + task.get("model_id"));
            System.out.println("Preprocess Options: " + task.get("preprocess_options"));
            System.out.println("Video A Duration: " + task.get("video_a_duration") + "s, Res: " + task.get("video_a_resolution") + ", Path: " + task.get("video_a_path"));
            System.out.println("Video B Duration: " + task.get("video_b_duration") + "s, Res: " + task.get("video_b_resolution") + ", Path: " + task.get("video_b_path"));
            
            // 2. Dump Task Results
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                    "SELECT * FROM task_results WHERE task_id = ?", taskId
            );
            if (!results.isEmpty()) {
                Map<String, Object> res = results.get(0);
                System.out.println("--- Results ---");
                System.out.println("Dimension Scores: " + res.get("dimension_scores"));
                System.out.println("Similar Points: " + res.get("similar_points"));
                System.out.println("Difference Points: " + res.get("difference_points"));
                System.out.println("Summary: " + res.get("summary"));
                System.out.println("Key Frames A: " + res.get("key_frames_a"));
                System.out.println("Key Frames B: " + res.get("key_frames_b"));
            } else {
                System.out.println("--- No Results Found in task_results ---");
            }

            // 3. Dump Model Call Logs
            List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                    "SELECT * FROM model_call_logs WHERE task_id = ? OR task_name = ? ORDER BY started_at DESC", taskId, taskName
            );
            System.out.println("--- Model Call Logs (" + logs.size() + ") ---");
            for (Map<String, Object> log : logs) {
                System.out.println("Log ID: " + log.get("id") + ", Status Code: " + log.get("status_code"));
                System.out.println("Model URL: " + log.get("model_url"));
                
                String req = (String) log.get("request_payload");
                if (req != null && req.length() > 2000) {
                    System.out.println("Request Payload (truncated): " + req.substring(0, 1000) + " ... [TRUNCATED] ... " + req.substring(req.length() - 500));
                } else {
                    System.out.println("Request Payload: " + req);
                }
                
                String resp = (String) log.get("response_body");
                if (resp != null && resp.length() > 2000) {
                    System.out.println("Response Body (truncated): " + resp.substring(0, 1000) + " ... [TRUNCATED] ... " + resp.substring(resp.length() - 500));
                } else {
                    System.out.println("Response Body: " + resp);
                }
            }
        }
        System.out.println("==================================================");
    }
}
