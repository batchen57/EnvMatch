package com.envmatch.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ModelCallLogSchemaService implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public ModelCallLogSchemaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("model_call_logs")) return;
        String idType = idColumnType();
        if (idType.contains("INT")) return;

        jdbcTemplate.execute("ALTER TABLE model_call_logs RENAME TO model_call_logs_legacy");
        createTable();
        jdbcTemplate.execute("""
                INSERT INTO model_call_logs (
                    task_id, task_name, model_id, model_url, request_payload, response_body,
                    started_at, ended_at, status_code, input_tokens, output_tokens
                )
                SELECT
                    task_id, task_name, model_id, model_url, request_payload, response_body,
                    started_at, ended_at, status_code, input_tokens, output_tokens
                FROM model_call_logs_legacy
                """);
        jdbcTemplate.execute("DROP TABLE model_call_logs_legacy");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private String idColumnType() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("PRAGMA table_info(model_call_logs)");
        for (Map<String, Object> column : columns) {
            if ("id".equalsIgnoreCase(String.valueOf(column.get("name")))) {
                return String.valueOf(column.get("type")).toUpperCase(Locale.ROOT);
            }
        }
        return "";
    }

    private void createTable() {
        jdbcTemplate.execute("""
                CREATE TABLE model_call_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id VARCHAR(36),
                    task_name VARCHAR,
                    model_id VARCHAR,
                    model_url TEXT,
                    request_payload TEXT,
                    response_body TEXT,
                    started_at DATETIME,
                    ended_at DATETIME,
                    status_code VARCHAR,
                    input_tokens FLOAT,
                    output_tokens FLOAT
                )
                """);
    }
}
