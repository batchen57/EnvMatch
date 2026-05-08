# CLAUDE.md

## Project Overview

EnvMatch AI — 视频环境相似度对比平台 (v1.2.0)。用户上传两段视频，系统通过多模态大模型分析背景环境的相似度，输出综合评分、维度评分、相似点/差异点及分析报告。支持多种 AI 模型 (Gemini, GPT-4o, Qwen-VL, MiniMax 等) 和识别模式 (图片模式/视频模式)。

## Tech Stack

- **Backend**: Python 3, FastAPI, SQLAlchemy, SQLite (可切换 PostgreSQL)
- **Frontend**: React 19, TypeScript, Vite 8, Tailwind CSS v4, ECharts, React Router v7, Framer Motion, Radix UI, dnd-kit
- **AI**: 多模型支持 — Google Gemini (原生视频), OpenAI 兼容接口 (GPT-4o, Qwen3-VL, MiniMax, DeepSeek 等)
- **Video Processing**: FFmpeg (抽帧/预处理), PySceneDetect (场景检测), OpenCV (光流分析)

## Project Structure

```
EnvMatch/
├── backend/
│   ├── main.py              # FastAPI 应用入口, CORS 配置, 所有 API 路由 + Pydantic schemas
│   ├── models.py            # SQLAlchemy 模型: Task, TaskResult, AIModel, PromptTemplate
│   ├── database.py          # 数据库引擎 (SQLite 默认, PostgreSQL 可切换), get_db 依赖
│   ├── requirements.txt     # Python 依赖
│   ├── services/
│   │   └── task_processor.py  # 后台异步任务: 预处理 → 抽帧 → 多模型 AI 分析 → 入库 + Webhook
│   └── storage/             # 上传视频 + 抽帧结果 (按 task_id 组织)
├── frontend/
│   ├── src/
│   │   ├── main.tsx         # React 入口, BrowserRouter
│   │   ├── App.tsx          # 根布局 (Sidebar + Header + Routes)
│   │   ├── components/
│   │   │   ├── Sidebar.tsx      # 侧边导航 (5 项: 工作台/任务管理/结果报表/提示词配置/模型管理)
│   │   │   ├── Pipeline.tsx     # 5 步分析流程可视化 (静态展示)
│   │   │   ├── Overview.tsx     # 仪表盘: 统计卡片 + ECharts 趋势图 + 分布饼图
│   │   │   ├── TaskList.tsx     # 任务列表: 表格 + 状态筛选标签 + 5s 自动轮询 + 分页
│   │   │   ├── TaskCreation.tsx # 新建任务向导 (5 步可拖拽模态框)
│   │   │   ├── ResultDetails.tsx # 结果详情: 环形图 + 雷达图 + 视频播放 + AI 分析 + 关键帧 + PDF 导出
│   │   │   ├── ResultReport.tsx # 结果报表: 统计卡片 + 雷达图 + 模型性能图 + 趋势图 + 分布图
│   │   │   ├── ModelConfig.tsx  # 模型管理: CRUD + 拖拽排序 + 预设模板 + 能力标签
│   │   │   └── PromptConfig.tsx # 提示词管理: CRUD + 搜索 + 卡片网格
│   │   └── lib/utils.ts     # cn() 工具函数 (clsx + tailwind-merge)
│   ├── index.html
│   ├── vite.config.ts       # Vite 配置: React 插件 + Tailwind v4 + @ 别名
│   └── package.json
└── CLAUDE.md
```

## Backend API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/tasks/` | 创建任务 (multipart: task_name, video_a, video_b, model_id, prompt, preprocess_options) |
| GET | `/tasks/` | 任务列表 (支持 status/skip/limit, 返回各状态计数) |
| GET | `/tasks/{task_id}` | 获取任务详情及结果 (含 TaskResult) |
| DELETE | `/tasks/{task_id}` | 删除任务、结果及关联存储文件 |
| GET | `/dashboard-stats` | 仪表盘统计数据 (含趋势/分布/模型汇总/维度均值) |
| POST | `/cleanup` | 清理超过 N 天的存储文件 |
| GET | `/prompt-templates/` | 提示词模板列表 (首次自动 seed 默认模板) |
| POST | `/prompt-templates/` | 创建提示词模板 |
| PUT | `/prompt-templates/{id}` | 更新提示词模板 |
| DELETE | `/prompt-templates/{id}` | 删除提示词模板 |
| GET | `/models/` | AI 模型列表 (首次自动 seed 5 个预设模型) |
| POST | `/models/` | 创建 AI 模型 |
| PUT | `/models/{model_id}` | 更新 AI 模型 |
| DELETE | `/models/{model_id}` | 删除 AI 模型 |

## Data Models

**Task**: id (UUID), task_name, video_a_path, video_b_path, status (PENDING/PROCESSING/COMPLETED/FAILED), similarity_score, model_id, prompt, input_tokens, output_tokens, video_a_duration, video_b_duration, video_a_resolution, video_b_resolution, video_a_size, video_b_size, preprocess_options (JSON), created_at, updated_at

**TaskResult**: task_id (FK→Task), dimension_scores (JSON: architecture/vegetation/lighting_weather/facilities/road_surface), similar_points (JSON), difference_points (JSON), summary, key_frames_a/b (JSON), error_message, input_tokens, output_tokens

**AIModel**: id (UUID), name, identifier, provider, api_key, base_url, description, capabilities (JSON: [text/image/video]), is_default, sort_order, created_at, updated_at

**PromptTemplate**: id (UUID), name, content, created_at, updated_at

## Task Processing Pipeline

1. `POST /tasks/` → 保存视频文件到 `storage/`, 创建 PENDING 状态 Task
2. 后台 `process_video_task()`:
   - 状态 → PROCESSING, 读取 preprocess_options
   - 可选预处理: FFmpeg 缩放/转格式/去噪 (`preprocess_video`)
   - 抽帧 (`extract_frames`):
     - 图片输入 (jpg/png/bmp/webp): 直接复制
     - 固定采样 (fixed): FFmpeg 按 fps 抽帧, 缩放到目标分辨率
     - 感知采样 (perceptual): PySceneDetect 场景检测 + OpenCV 光流分析, 上限 20 帧
   - AI 分析路由:
     - `recognition_mode="video"` + Gemini → `analyze_environment_with_gemini()` (原生视频上传)
     - `recognition_mode="video"` + Qwen → `analyze_with_qwen_video()` (base64 视频)
     - 其他 → `analyze_with_openai_compatible()` (首帧图片 + OpenAI 兼容 API)
   - 解析 AI 返回的 JSON (含 Markdown 代码块剥离)
   - 状态 → COMPLETED, 写入 TaskResult, 更新 Task (score/tokens)
   - 失败 → FAILED, 写入 error_message
   - 可选: 通过 `WEBHOOK_URL` 环境变量发送完成通知

## Frontend Routes

| Path | Component | Description |
|------|-----------|-------------|
| `/` | — | 重定向到 `/dashboard` |
| `/dashboard` | Pipeline + Overview | 主仪表盘 |
| `/tasks` | TaskList | 任务管理列表 |
| `/tasks/:id` | ResultDetails | 单任务结果详情 |
| `/results` | ResultReport | 结果报表 |
| `/prompts` | PromptConfig | 提示词模板管理 |
| `/models` | ModelConfig | AI 模型管理 |

## Running Locally

**Backend**:
```bash
cd backend
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

**Frontend** (需要 Node.js 18+):
```bash
cd frontend
npm install
npm run dev  # 默认 http://localhost:5173
```

前端 API 请求硬编码为 `http://localhost:8000`, 需要后端先启动。

## Key Notes

- Database: 默认 SQLite (`envmatch.db`), `connect_args={"check_same_thread": False}` 用于 FastAPI 异步环境
- CORS: 允许所有来源 (`allow_origins=["*"]`)
- FFmpeg: 必须安装并在 PATH 中, 抽帧失败不会阻断流程 (降级处理)
- AI 模型: 通过 `/models/` 接口管理, 首次访问自动 seed 5 个预设模型 (Gemini/GPT-4o/MiniMax/Qwen)
- 提示词模板: 通过 `/prompt-templates/` 接口管理, 首次访问自动 seed 系统默认提示词
- 感知抽帧依赖 PySceneDetect + OpenCV, 固定抽帧仅需 FFmpeg
- 前端 TaskList 每 5 秒自动轮询刷新
- 视频文件存储在本地 `backend/storage/` 目录, 按 task_id 组织
- 深色主题 + 毛玻璃效果, CSS 变量定义在 `index.css`
- ResultDetails 支持 PDF 导出 (modern-screenshot + jsPDF)
