# CLAUDE.md

## Project Overview

EnvMatch AI 是一个视频/图片环境相似度对比平台。用户上传 A/B 两份素材，系统通过多模态模型分析背景环境，输出综合相似度、维度评分、相似点、差异点、总结及 Token 用量。

当前后端是 Java 17 + Spring Boot 实现。不要参考旧版 Python/FastAPI 目录结构或恢复已移除的 Python 依赖。

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.3.5, Spring MVC, MyBatis-Plus 3.5.7, PostgreSQL
- **Frontend**: React 19, TypeScript 6, Vite 8, Tailwind CSS 4, ECharts, React Router 7, Framer Motion, Radix UI, dnd-kit
- **AI**: Google Gemini API, OpenAI-compatible multimodal APIs, MiniMax native VLM endpoint
- **Media**: JavaCV (FFmpeg & OpenCV bindings), FFmpeg/FFprobe CLI (for fallback token estimation), Java ImageIO/Graphics2D
- **Tests**: JUnit 5, Spring Boot Test, AssertJ, Mockito

## Project Structure

```text
EnvMatch/
├── backend/
│   ├── pom.xml
│   ├── PERCEPTUAL_SAMPLING.md
│   └── src/
│       ├── main/
│       │   ├── java/com/envmatch/
│       │   │   ├── EnvMatchApplication.java
│       │   │   ├── config/       # CORS, static storage mapping, bounded async executor
│       │   │   ├── mapper/       # MyBatis-Plus mappers
│       │   │   ├── model/        # Database models
│       │   │   ├── service/      # storage, video, AI, task processing, seed/recovery
│       │   │   └── web/          # REST controller and request DTOs
│       │   └── resources/application.properties
│       └── test/java/com/envmatch/
│           ├── TaskWorkflowIntegrationTest.java
│           └── service/
├── frontend/
│   ├── src/
│   │   ├── App.tsx
│   │   ├── components/
│   │   └── lib/utils.ts
│   ├── package.json
│   └── vite.config.ts
├── README.md
└── RELEASENOTES.md
```

## Backend Responsibilities

- `ApiController`: API boundary validation, CRUD, task creation/deletion, dashboard aggregation and cleanup.
- `TaskProcessingService`: asynchronous task state transitions, metadata, extraction, optional compression, AI analysis, result persistence and Webhook.
- `VideoService`: FFprobe metadata, FFmpeg extraction/compression and deterministic perceptual sampling.
- `AiAnalysisService`: provider-specific payloads, HTTP model calls, JSON result parsing, Token calculation and sanitized audit logs.
- `StorageService`: upload storage under the configured storage root.
- `StartupRecoveryService`: marks interrupted `PENDING`/`PROCESSING` tasks as `FAILED` on startup.

## API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/tasks/` | Create an async comparison task from multipart data |
| GET | `/tasks/` | List tasks with `status`, `skip`, `limit` |
| GET | `/tasks/{taskId}` | Return task and optional result |
| DELETE | `/tasks/{taskId}` | Delete task, result and generated media |
| GET | `/dashboard-stats` | Return totals, trends, distribution and model summaries |
| POST | `/cleanup?days=7` | Delete old files from storage |
| GET/POST | `/prompt-templates/` | List/create prompt templates |
| PUT/DELETE | `/prompt-templates/{templateId}` | Update/delete prompt templates |
| GET/POST | `/models/` | List/create model configurations |
| PUT/DELETE | `/models/{modelId}` | Update/delete model configurations |
| GET | `/model-logs/` | Search and paginate model audit logs |
| GET | `/storage/**` | Serve uploaded and generated media |

JSON uses `snake_case`. The frontend currently calls `http://localhost:8888` directly, so keep API compatibility unless the frontend is updated in the same change.

## Task Processing Pipeline

1. Validate multipart fields and `preprocess_options`.
2. Save both uploads under `envmatch.storage-dir`.
3. Persist a `PENDING` task and submit it to the bounded `taskExecutor`.
4. Mark the task `PROCESSING`; probe duration, resolution, size and FPS.
5. Extract fixed or perceptual frames and persist a partial result for UI preview.
6. Optionally compress native-video inputs to the requested resolution.
7. Build an image grid or native-video payload and invoke the configured model.
8. Sanitize and persist request/response audit data.
9. Persist scores, result details and Token usage; mark the task `COMPLETED` or `FAILED`.
10. Send an optional completion/failure Webhook.

## Preprocessing Options Schema

`preprocess_options` is passed as a JSON string to `POST /tasks/` containing:
- `recognition_mode`: `"image"` | `"video"` (default: `"image"`)
- `sampling_type`: `"fixed"` | `"perceptual"` (default: `"fixed"`)
- `sampling_fps`: `1` to `5` (default: `1`)
- `clip_start_seconds`: double (default: `0.0`, must be >= `0.0`)
- `clip_end_seconds`: double (default: `15.0`, must be > `clip_start_seconds`)
- `resolution`: boolean (optional)
- `resolution_val`: `90` to `2160` (default: `720`, target height)

## Perceptual Sampling Contract

`backend/PERCEPTUAL_SAMPLING.md` is the source of truth:

- Sample preview candidates at 2 FPS.
- Compare 32x18 luminance signatures and 16-bin histograms.
- Always retain first and last frames.
- Retain frames when histogram difference exceeds `0.30` or accumulated luminance motion exceeds `0.50`.
- Produce chronological, duplicate-free output with at most 10 frames.
- Fall back to fixed extraction if needed and fill temporal coverage to at least five frames when available.

Threshold or output-limit changes require updating both the contract document and regression tests.

## Configuration

Defaults are in `backend/src/main/resources/application.properties`.

| Property | Default | Purpose |
|---|---:|---|
| `server.port` | `8888` | HTTP port |
| `spring.datasource.url` | `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:envmatch}` | PostgreSQL database |
| `envmatch.storage-dir` | `storage` | Upload and generated-file root |
| `envmatch.ai.max-inline-media-bytes` | `16777216` | Combined inline-video size limit |
| `envmatch.task-executor.core-size` | `2` | Core async workers |
| `envmatch.task-executor.max-size` | `4` | Maximum async workers |
| `envmatch.task-executor.queue-capacity` | `50` | Pending task capacity |
| `envmatch.webhook-url` / `WEBHOOK_URL` | empty | Task completion/failure callback |

Multipart limits are 2048 MB per file and 4096 MB per request.

## Running and Verification

```bash
cd backend
mvn spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

Before finishing backend changes:

```bash
cd backend
mvn test
```

Before finishing frontend changes:

```bash
cd frontend
npm run lint
npm run build
```

## Implementation Notes

- Keep database compatibility; avoid vendor-specific JPA behavior unless migration support is included.
- The task executor is intentionally bounded to protect JVM memory and reduce database connection lock contention.
- Task creation spans filesystem and database operations, so preserve compensating cleanup on partial failure.
- Do not write raw video Base64 or very large Base64 fields to audit logs.
- Native video automatically falls back to sampled frames when files exceed the inline-media limit.
- `TaskResult` may exist while a task is still processing because key frames are saved early for preview.
- Static media is exposed from the configured storage directory through `/storage/**`.
- The frontend task list polls every five seconds and resets to the first unfiltered page after creating a task.
