# CLAUDE.md

## Project Overview

EnvMatch AI — 视频环境相似度对比平台。用户上传两段视频，系统通过多模态大模型 (Google Gemini) 分析背景环境的相似度，输出综合评分、维度评分、相似点/差异点及分析报告。

## Tech Stack

- **Backend**: Python 3, FastAPI, SQLAlchemy, SQLite (可切换 PostgreSQL)
- **Frontend**: React 19, TypeScript, Vite 8, Tailwind CSS v4, ECharts, React Router v7
- **AI**: Google Gemini API (gemini-2.5-pro)
- **Video Processing**: FFmpeg (抽帧, 1fps, 缩放到 1280px 长边)

## Project Structure

```
EnvMatch/
├── backend/
│   ├── main.py              # FastAPI 应用入口, CORS 配置, 所有 API 路由
│   ├── models.py            # SQLAlchemy 模型: Task, TaskResult (枚举状态 + JSON 字段)
│   ├── database.py          # 数据库引擎 (SQLite 默认, PostgreSQL 可切换), get_db 依赖
│   ├── requirements.txt     # Python 依赖
│   ├── services/
│   │   └── task_processor.py  # 后台异步任务: FFmpeg 抽帧 → Gemini 分析 → 入库 + Webhook
│   └── storage/             # 上传视频 + 抽帧结果 (按 task_id 组织)
├── frontend/
│   ├── src/
│   │   ├── main.tsx         # React 入口, BrowserRouter
│   │   ├── App.tsx          # 根布局 (Sidebar + Header + Routes)
│   │   ├── components/
│   │   │   ├── Sidebar.tsx      # 侧边导航 (7 项, 部分路由未实现)
│   │   │   ├── Pipeline.tsx     # 5 步分析流程可视化 (静态展示)
│   │   │   ├── Overview.tsx     # 仪表盘: 统计卡片 + ECharts 趋势图 + 分布饼图
│   │   │   ├── TaskList.tsx     # 任务列表: 表格 + 状态标签 + 5s 自动轮询
│   │   │   ├── TaskCreation.tsx # 新建任务向导 (5 步模态框)
│   │   │   └── ResultDetails.tsx # 结果详情: 环形图 + 雷达图 + AI 分析 + 关键帧
│   │   └── lib/utils.ts     # cn() 工具函数 (clsx + tailwind-merge)
│   ├── index.html
│   ├── vite.config.ts       # Vite 配置: React 插件 + Tailwind v4 + @ 别名
│   └── package.json
└── CLAUDE.md
```

## Backend API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/tasks/` | 创建任务 (multipart form: task_name, video_a, video_b) |
| GET | `/tasks/{task_id}` | 获取任务详情及结果 (含 TaskResult) |
| GET | `/tasks/` | 任务列表 (支持 skip/limit 分页) |
| GET | `/dashboard-stats` | 仪表盘统计数据 |
| POST | `/cleanup` | 清理超过 N 天的存储文件 |

## Data Models

**Task**: id, task_name, video_a_path, video_b_path, status (PENDING/PROCESSING/COMPLETED/FAILED), similarity_score, model_id, created_at, updated_at

**TaskResult**: task_id (FK→Task), dimension_scores (JSON), similar_points (JSON), difference_points (JSON), summary, key_frames_a/b (JSON, 最多 6 帧路径)

## Task Processing Pipeline

1. `POST /tasks/` → 保存视频文件到 `storage/`, 创建 PENDING 状态 Task
2. 后台 `process_video_task()`:
   - 状态 → PROCESSING
   - `extract_frames()`: FFmpeg 以 1fps 抽帧, 缩放到 1280px, 输出到 `storage/{task_id}_{A/B}_frames/`
   - `analyze_environment_with_gemini()`: 上传视频文件到 Gemini, 发送分析 prompt (JSON 格式输出), 解析返回结果
   - 如果未配置 `GEMINI_API_KEY`, 返回 mock 数据
   - 状态 → COMPLETED, 写入 TaskResult
   - 可选: 通过 `WEBHOOK_URL` 环境变量发送完成通知

## Frontend Routes

| Path | Component | Description |
|------|-----------|-------------|
| `/dashboard` | Dashboard (Pipeline + Overview) | 主仪表盘 |
| `/tasks` | TaskList | 任务管理列表 |
| `/tasks/:id` | ResultDetails | 单任务结果详情 |
| `/results`, `/reports`, `/datasets`, `/models`, `/settings` | — | 侧边栏已定义路由但未实现 |

## Running Locally

**Backend**:
```bash
cd backend
pip install -r requirements.txt
set GEMINI_API_KEY=<your-key>  # 可选, 不设则使用 mock 数据
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
- Gemini prompt 要求严格 JSON 输出, 有 Markdown 代码块剥离逻辑
- 前端 TaskList 每 5 秒自动轮询刷新任务列表
- 所有视频文件存储在本地 `backend/storage/` 目录, 无对象存储集成
