# EnvMatch 视频环境相似度对比平台

EnvMatch 是一款基于多模态大模型的生产级视频环境分析平台。它能够深度比对两段视频（或图片）的背景环境相似度，忽略主体动作，专注于地貌、建筑、光影及陈设等核心环境要素，并生成详尽的量化评分报告。

## 🚀 核心功能

*   **多模态对比分析**：支持 Gemini 2.0 Pro、GPT-4o、Qwen-VL-Max 及 **MiniMax-M2.7 (原生 VLM)** 等前沿模型。
*   **智能资产管理系统**：
    *   **模型配置中心**：支持拖拽排序 (DND) 与官方预设一键加载，灵活掌控 AI 路由优先级。
    *   **提示词模版库**：集成 Markdown 编辑器的提示词管理工具，支持快速搜索与精准指令分发。
*   **工业级预处理管线**：
    *   **视频剪辑与区间提取**：支持自定义 A/B 视频截取时间段（`clip_start_seconds` 至 `clip_end_seconds`），仅处理和分析选中区间，大幅提高处理和推理效率，节省 Token。
    *   **动态采样**：支持 1-5 FPS 自定义抽帧及**智能感知抽帧（Perceptual Sampling）**，通过亮度签名与直方图差异自动捕获环境变化。
    *   **对比网格缝合（Image Stitching）**：针对原生 VLM 优化的图像矩阵技术，使用 Java ImageIO/Graphics2D 将采样帧缝合成一个对比网格图（上行为视频 A，下行为视频 B），实现单次推理下的 A/B 画面直观对比。
    *   **智能压缩**：物理级 360p-1080p 视频压制，平衡传输带宽与模型识别率。
    *   **画质增强**：FFmpeg 专业级降噪滤镜，提升低光、噪点素材的特征提取成功率。
*   **可视化数据中枢**：
    *   **深度相似度看板**：多维雷达图（建筑、光影、植被、地理）直观呈现环境拟合度。
    *   **模型调用审计 (Audit Logs)**：全量记录每次 VLM API 的 Request/Response、状态码及耗时，支持 JSON 报文全局搜索与还原（对超大 Base64 进行脱敏以防数据库膨胀）。
    *   **交互式图片审计器 (Image Viewer Pro)**：
        *   **自动化流提取**：从 Request Payload 中自动提取 Base64 图片，还原 AI 看到的真实画面。
        *   **交互审计能力**：支持 **100% 原始比例** 开启、**50%-500%** 缩放平移及多图快速导航，实现像素级的输入核验。
    *   **Token 精准统计**：集成 **Qwen-VL 专用 Token 估算算法**（基于 `smart_nframes` 与 `smart_resize` 计算），并在审计列表中实时展示“总消耗Token”，优化成本管控。
*   **极致交互体验**：
    *   采用深色模式与 Glassmorphism 设计语言。
    *   **交互式报文分析器**：支持模型入参/出参的悬浮截断展示、全屏 JSON 格式化查看（支持嵌套转义自动解包）及窗口自由拖拽。

## 🧠 识别技术原理

EnvMatch 提供两种核心识别模式，以适配不同的业务场景与模型能力：

### 1. 图像 LLM 识别 (Stitched Matrix Inference)
这是平台的**高精度默认模式**，适用于所有主流多模态模型（如 GPT-4o, MiniMax-M2.7, Qwen-VL）。
*   **实现原理**：
    1.  **区间限制与多点采样**：从 A/B 视频在截取区间内提取关键帧，支持固定 FPS 或基于亮度签名、直方图差异的感知采样；感知模式最多保留 10 帧并保证时间覆盖。
    2.  **矩阵缝合 (Image Stitching)**：使用 Java ImageIO/Graphics2D 将采样帧缝合成一个对比网格图。
    3.  **空间对比推理**：将拼接图作为单条消息发送，让模型在同一视觉空间内直接比对环境特征。
*   **核心优势**：**极高对比精度**。AI 可以在同一个坐标系下直接观察 A/B 差异，极大地减少了由于分次识别导致的记忆幻觉，且显著节省 Token 成本。

### 2. 视频 LLM 识别 (Native Video Analysis)
该模式专为具备原生视频理解能力的长上下文模型（如 Qwen-VL-Plus, Gemini 1.5 Pro）设计。
*   **实现原理**：
    1.  **原生流投喂 (Native Video Payload)**：通过 Gemini 原生接口或 OpenAI 兼容多模态接口投喂视频（仅包含截取区间）；超出内联媒体大小限制时自动降级为采样帧。
    2.  **物理预处理 (FFmpeg Hardening)**：支持物理级 360p-1080p 视频压制，在投喂 AI 前进行硬件级规格缩放以优化识别效率。
    3.  **专业 Token 估算 (Specialized Counting)**：集成 Qwen 官方推荐的视频 Token 计算公式：基于 `smart_nframes` (抽帧计算) 与 `smart_resize` (动态缩放) 精准预估负载。
*   **核心优势**：**动态环境理解**。能够捕捉摄像机移动过程中的全局环境一致性，适合分析具有复杂空间漫游特征的视频素材。

## 🛠️ 技术架构

*   **前端**：React 19 + TypeScript 6 + Vite 8 + Tailwind CSS 4 + ECharts + dnd-kit
*   **后端**：Java 17 + Spring Boot 3.3 + Spring Data JPA + SQLite
*   **多媒体处理**：FFmpeg / FFprobe + Java ImageIO
*   **AI 引擎**：Google Gemini API、OpenAI 兼容多模态接口及 MiniMax 原生 VLM 接口

## 📦 快速启动

### 1. 环境依赖
*   JDK 17+
*   Maven 3.9+
*   Node.js 18+
*   **FFmpeg** (必须安装并添加至系统 PATH)

### 2. 后端启动
```bash
cd backend
mvn spring-boot:run
```

后端默认监听 `http://localhost:8888`，数据库和上传文件分别保存到
`backend/envmatch.db` 与 `backend/storage/`。

### 3. 前端启动
```bash
cd frontend
npm install
npm run dev
```

### 4. 验证与构建
```bash
cd backend
mvn test

cd ../frontend
npm run lint
npm run build
```

## ⚙️ 后端配置

主要配置位于 `backend/src/main/resources/application.properties`：

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `server.port` | `8888` | 后端服务端口 |
| `envmatch.storage-dir` | `storage` | 上传、抽帧和预处理文件目录 |
| `envmatch.ai.max-inline-media-bytes` | `16777216` | 原生视频内联载荷上限 |
| `envmatch.task-executor.core-size` | `2` | 异步任务核心线程数 |
| `envmatch.task-executor.max-size` | `4` | 异步任务最大线程数 |
| `envmatch.task-executor.queue-capacity` | `50` | 待处理任务队列容量 |

可通过 `WEBHOOK_URL` 环境变量配置任务完成/失败回调。

## 📖 使用指南
1. 进入 **模型管理**：可选择官方预设（如 Gemini 2.0 Pro）快速填充配置，或手动输入 API Key。支持通过拖拽卡片直接调整模型调用优先级。
2. 配置 **提示词模版**：在模版库中预设您的环境分析指令，利用 Markdown 语法明确评分规则。
3. 创建 **任务分析流**：在任务中心上传 A/B 视频素材，配置处理层的采样策略、**视频截取区间**与压缩参数。**视频模式**下可选择物理压制以节省 Token。
4. **模型调用审计**：通过“模型调用记录”菜单实时监控 API 交互详情，支持对 Payload 进行全屏分析、复制，并自动展示提取出的图片。
5. 查看 **分析报告**：系统生成深度的多维对比报告，支持关键帧回溯与视频在线回放。

---
*Powered by Advanced Agentic Coding Team*
