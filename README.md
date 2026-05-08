# EnvMatch 视频环境相似度对比平台

EnvMatch 是一款基于多模态大模型的生产级视频环境分析平台。它能够深度比对两段视频（或图片）的背景环境相似度，忽略主体动作，专注于地貌、建筑、光影及陈设等核心环境要素，并生成详尽的量化评分报告。

## 🚀 核心功能

*   **多模态对比分析**：支持 Gemini 2.0 Pro、GPT-4o、Qwen-VL-Max 及 DeepSeek 等前沿原生视频/多模态模型。
*   **智能资产管理系统**：
    *   **模型配置中心**：支持拖拽排序 (DND) 与官方预设一键加载，灵活掌控 AI 路由优先级。
    *   **提示词模版库**：集成 Markdown 编辑器的提示词管理工具，支持快速搜索与精准指令分发。
*   **工业级预处理管线**：
    *   **动态采样**：支持 1-5 FPS 自定义抽帧，适配不同分析精度需求。
    *   **智能压缩**：物理级 360p-1080p 视频压制，平衡传输带宽与模型识别率。
    *   **画质增强**：FFmpeg 专业级降噪滤镜，提升低光、噪点素材的特征提取成功率。
*   **可视化数据中枢**：
    *   **深度相似度看板**：多维雷达图（建筑、光影、植被、地理）直观呈现环境拟合度。
    *   **Token 审计**：端到端记录模型消耗，辅助成本核算与效率分析。
*   **极致交互体验**：采用深色模式与 Glassmorphism 设计语言，适配各种专业分析场景。

## 🛠️ 技术架构

*   **前端**：React 18 + Vite + Tailwind CSS + ECharts + dnd-kit (拖拽排序)
*   **后端**：FastAPI + SQLAlchemy (SQLite) + FFmpeg (多媒体处理)
*   **AI 引擎**：Google Gemini API / OpenAI API / Alibaba DashScope

## 📦 快速启动

### 1. 环境依赖
*   Python 3.10+
*   Node.js 18+
*   **FFmpeg** (必须安装并添加至系统 PATH)

### 2. 后端启动
```bash
cd backend
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --port 8000
```

### 3. 前端启动
```bash
cd frontend
npm install
npm run dev
```

## 📖 使用指南
1. 进入 **模型管理**：可选择官方预设（如 Gemini 2.0 Pro）快速填充配置，或手动输入 API Key。支持通过拖拽卡片直接调整模型调用优先级。
2. 配置 **提示词模版**：在模版库中预设您的环境分析指令，利用 Markdown 语法明确评分规则。
3. 创建 **任务分析流**：在任务中心上传 A/B 视频素材，配置处理层的压缩与增强参数。
4. 查看 **分析报告**：系统生成深度的多维对比报告，支持关键帧回溯与视频在线回放。

---
*Powered by Advanced Agentic Coding Team*
