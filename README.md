# EnvMatch 视频环境相似度对比平台

EnvMatch 是一款基于多模态大模型的生产级视频环境分析平台。它能够深度比对两段视频（或图片）的背景环境相似度，忽略主体动作，专注于地貌、建筑、光影及陈设等核心环境要素，并生成详尽的量化评分报告。

## 🚀 核心功能

*   **多模态对比分析**：支持 Gemini 2.0 Pro、GPT-4o、Qwen-VL-Max 及 **MiniMax-M2.7 (原生 VLM)** 等前沿模型。
*   **智能资产管理系统**：
    *   **模型配置中心**：支持拖拽排序 (DND) 与官方预设一键加载，灵活掌控 AI 路由优先级。
    *   **提示词模版库**：集成 Markdown 编辑器的提示词管理工具，支持快速搜索与精准指令分发。
*   **工业级预处理管线**：
    *   **动态采样**：支持 1-5 FPS 自定义抽帧及**智能感知抽帧（Perceptual Sampling）**，自动捕获运动关键点。
    *   **对比网格缝合（Image Stitching）**：针对原生 VLM 优化的图像矩阵技术，实现单次推理下的 A/B 画面直观对比。
    *   **智能压缩**：物理级 360p-1080p 视频压制，平衡传输带宽与模型识别率。
    *   **画质增强**：FFmpeg 专业级降噪滤镜，提升低光、噪点素材的特征提取成功率。
*   **可视化数据中枢**：
    *   **深度相似度看板**：多维雷达图（建筑、光影、植被、地理）直观呈现环境拟合度。
    *   **模型调用审计 (Audit Logs)**：全量记录每次 VLM API 的 Request/Response、状态码及耗时，支持 JSON 报文全局搜索与溯源。
    *   **交互式图片审计器 (Image Viewer Pro)**：
        *   **自动化流提取**：从 Request Payload 中自动提取 Base64 图片，还原 AI 看到的真实画面。
        *   **交互审计能力**：支持 **100% 原始比例** 开启、**50%-500%** 缩放平移及多图快速导航，实现像素级的输入核验。
    *   **Token 精准统计**：集成 **Qwen-VL 专用 Token 估算算法**（适配分辨率/FPS/时长），并在审计列表中实时展示“总消耗Token”，优化成本管控。
*   **极致交互体验**：
    *   采用深色模式与 Glassmorphism 设计语言。
    *   **交互式报文分析器**：支持模型入参/出参的悬浮截断展示、全屏 JSON 格式化查看及窗口自由拖拽。

## 🧠 识别技术原理

EnvMatch 提供两种核心识别模式，以适配不同的业务场景与模型能力：

### 1. 图像 LLM 识别 (Stitched Matrix Inference)
这是平台的**高精度默认模式**，适用于所有主流多模态模型（如 GPT-4o, MiniMax-M2.7, Qwen-VL）。
*   **实现原理**：
    1.  **多点采样**：从 A/B 视频中提取 3-5 组关键帧（支持固定 FPS 或基于光流的感知采样）。
    2.  **矩阵缝合 (Image Stitching)**：利用 OpenCV 将采样帧缝合成一个对比网格图（上行为视频 A，下行为视频 B）。
    3.  **空间对比推理**：将拼接图作为单条消息发送，让模型在同一视觉空间内直接比对环境特征。
*   **核心优势**：**极高对比精度**。AI 可以在同一个坐标系下直接观察 A/B 差异，极大地减少了由于分次识别导致的记忆幻觉，且显著节省 Token 成本。

### 2. 视频 LLM 识别 (Native Video Analysis)
该模式专为具备原生视频理解能力的长上下文模型（如 Qwen-VL-Plus, Gemini 1.5 Pro）设计。
*   **实现原理**：
    1.  **原生流投喂 (SDK Integration)**：采用 **DashScope SDK** 直接对接 Qwen 系列模型，利用模型的原生视频感知能力。
    2.  **物理预处理 (FFmpeg Hardening)**：支持物理级 360p-1080p 视频压制，在投喂 AI 前进行硬件级规格缩放以优化识别效率。
    3.  **专业 Token 估算 (Specialized Counting)**：集成 Qwen 官方推荐的视频 Token 计算公式：基于 `smart_nframes` (抽帧计算) 与 `smart_resize` (动态缩放) 精准预估负载。
*   **核心优势**：**动态环境理解**。能够捕捉摄像机移动过程中的全局环境一致性，适合分析具有复杂空间漫游特征的视频素材。

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
uvicorn main:app --port 8888
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
3. 创建 **任务分析流**：在任务中心上传 A/B 视频素材，配置处理层的采样策略与压缩参数。**视频模式**下可选择物理压制以节省 Token。
4. **模型调用审计**：通过“模型调用记录”菜单实时监控 API 交互详情，支持对 Payload 进行全屏分析与复制。
5. 查看 **分析报告**：系统生成深度的多维对比报告，支持关键帧回溯与视频在线回放。

---
*Powered by Advanced Agentic Coding Team*
