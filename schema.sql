-- =============================================================================
-- EnvMatch 视频环境相似度对比平台 - 数据库结构脚本 (PostgreSQL)
-- 生成时间: 2026-06-07
-- 说明: 本脚本根据 Spring Boot Java 实体类 (com.envmatch.model) 自动映射并精心优化生成。
--       包含了表结构、主外键关系、索引以及字段和表的详细中文注释。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. AI模型配置表 (ai_models)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_models (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    identifier VARCHAR(255) NOT NULL,
    provider VARCHAR(255) NOT NULL,
    api_key TEXT,
    base_url TEXT,
    description TEXT,
    capabilities TEXT,
    is_default VARCHAR(255) DEFAULT 'false',
    sort_order DOUBLE PRECISION DEFAULT 0.0,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_models IS 'AI大模型配置信息表';
COMMENT ON COLUMN ai_models.id IS '主键ID，UUID格式';
COMMENT ON COLUMN ai_models.name IS '模型友好名称，例如：GPT-4o, MiniMax-Text-to-Video';
COMMENT ON COLUMN ai_models.identifier IS '模型的唯一标识符/名称代号，例如：gpt-4o, abab6.5-chat';
COMMENT ON COLUMN ai_models.provider IS '模型服务商，例如：openai, minimax, deepseek';
COMMENT ON COLUMN ai_models.api_key IS '访问该模型的API密钥/凭证';
COMMENT ON COLUMN ai_models.base_url IS '模型的API基础调用路径URL';
COMMENT ON COLUMN ai_models.description IS '模型的详细描述或备注信息';
COMMENT ON COLUMN ai_models.capabilities IS '模型能力字典（如是否支持视频、最大token数等，以JSON文本存储）';
COMMENT ON COLUMN ai_models.is_default IS '是否为默认推荐模型，字符串类型：''true'' 或 ''false''';
COMMENT ON COLUMN ai_models.sort_order IS '排序权重，数字越小越靠前';
COMMENT ON COLUMN ai_models.created_at IS '记录创建时间';
COMMENT ON COLUMN ai_models.updated_at IS '记录最近修改时间';


-- -----------------------------------------------------------------------------
-- 2. 提示词模板表 (prompt_templates)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prompt_templates (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE prompt_templates IS '环境对比所用提示词（Prompt）模板表';
COMMENT ON COLUMN prompt_templates.id IS '主键ID，UUID格式';
COMMENT ON COLUMN prompt_templates.name IS '模板名称，例如：标准视频环境对比模板';
COMMENT ON COLUMN prompt_templates.content IS '提示词的模板具体内容，包含占位符';
COMMENT ON COLUMN prompt_templates.created_at IS '创建时间';
COMMENT ON COLUMN prompt_templates.updated_at IS '更新时间';


-- -----------------------------------------------------------------------------
-- 3. 对比任务表 (tasks)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tasks (
    id VARCHAR(36) PRIMARY KEY,
    task_name VARCHAR(255) NOT NULL,
    video_a_path VARCHAR(255),
    video_b_path VARCHAR(255),
    status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    similarity_score DOUBLE PRECISION,
    model_id VARCHAR(255),
    prompt TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    input_tokens DOUBLE PRECISION,
    output_tokens DOUBLE PRECISION,
    video_a_duration DOUBLE PRECISION,
    video_b_duration DOUBLE PRECISION,
    video_a_resolution VARCHAR(255),
    video_b_resolution VARCHAR(255),
    video_a_size DOUBLE PRECISION,
    video_b_size DOUBLE PRECISION,
    preprocess_options TEXT
);

COMMENT ON TABLE tasks IS '视频环境对比任务表';
COMMENT ON COLUMN tasks.id IS '主键任务ID，UUID格式';
COMMENT ON COLUMN tasks.task_name IS '任务名称，用于识别对比任务';
COMMENT ON COLUMN tasks.video_a_path IS '待对比视频A的本地存放路径或URL';
COMMENT ON COLUMN tasks.video_b_path IS '待对比视频B的本地存放路径或URL';
COMMENT ON COLUMN tasks.status IS '任务执行状态：PENDING(排队中), PROCESSING(处理中), COMPLETED(已完成), FAILED(失败)';
COMMENT ON COLUMN tasks.similarity_score IS '经大模型分析出的最终环境相似度总评分 (0-1.0)';
COMMENT ON COLUMN tasks.model_id IS '执行本任务时使用的AI模型ID (关联 ai_models.id)';
COMMENT ON COLUMN tasks.prompt IS '本次任务实际发送给大模型的完整提示词文本';
COMMENT ON COLUMN tasks.created_at IS '任务创建时间';
COMMENT ON COLUMN tasks.updated_at IS '任务最近一次更新状态的时间';
COMMENT ON COLUMN tasks.input_tokens IS '本次任务大模型调用的输入Token数量';
COMMENT ON COLUMN tasks.output_tokens IS '本次任务大模型调用的输出Token数量';
COMMENT ON COLUMN tasks.video_a_duration IS '视频A的总时长 (单位：秒)';
COMMENT ON COLUMN tasks.video_b_duration IS '视频B的总时长 (单位：秒)';
COMMENT ON COLUMN tasks.video_a_resolution IS '视频A的分辨率大小，如 1920x1080';
COMMENT ON COLUMN tasks.video_b_resolution IS '视频B的分辨率大小，如 1920x1080';
COMMENT ON COLUMN tasks.video_a_size IS '视频A文件的大小 (字节)';
COMMENT ON COLUMN tasks.video_b_size IS '视频B文件的大小 (字节)';
COMMENT ON COLUMN tasks.preprocess_options IS '视频切帧、预处理的配置参数选项（以JSON文本存储）';


-- -----------------------------------------------------------------------------
-- 4. 对比任务结果表 (task_results)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS task_results (
    task_id VARCHAR(36) PRIMARY KEY,
    dimension_scores TEXT,
    similar_points TEXT,
    difference_points TEXT,
    summary TEXT,
    key_frames_a TEXT,
    key_frames_b TEXT,
    error_message TEXT,
    input_tokens DOUBLE PRECISION,
    output_tokens DOUBLE PRECISION,
    CONSTRAINT fk_task_results_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE
);

COMMENT ON TABLE task_results IS '视频环境对比任务详细结果报告表';
COMMENT ON COLUMN task_results.task_id IS '任务ID，主键且外键关联 tasks.id (级联删除)';
COMMENT ON COLUMN task_results.dimension_scores IS '各个环境维度（如光照、布局、天气等）的对比得分（JSON文本格式）';
COMMENT ON COLUMN task_results.similar_points IS '两个视频中环境相似的具体要素描述点列表（JSON文本格式）';
COMMENT ON COLUMN task_results.difference_points IS '两个视频中环境差异的具体要素描述点列表（JSON文本格式）';
COMMENT ON COLUMN task_results.summary IS '大模型出具的最终对比结果详细综合文字总结';
COMMENT ON COLUMN task_results.key_frames_a IS '视频A中被选中参与对比分析的关键帧序列及描述（JSON文本格式）';
COMMENT ON COLUMN task_results.key_frames_b IS '视频B中被选中参与对比分析的关键帧序列及描述（JSON文本格式）';
COMMENT ON COLUMN task_results.error_message IS '如果任务运行失败，记录失败的具体异常/错误日志信息';
COMMENT ON COLUMN task_results.input_tokens IS '大模型在计算结果时的实际输入Token数（覆盖或保留任务层统计）';
COMMENT ON COLUMN task_results.output_tokens IS '大模型在计算结果时的实际输出Token数（覆盖或保留任务层统计）';


-- -----------------------------------------------------------------------------
-- 5. 模型接口调用日志表 (model_call_logs)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS model_call_logs (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(255),
    task_name VARCHAR(255),
    model_id VARCHAR(255),
    model_url TEXT,
    request_payload TEXT,
    response_body TEXT,
    started_at TIMESTAMP WITHOUT TIME ZONE,
    ended_at TIMESTAMP WITHOUT TIME ZONE,
    status_code VARCHAR(255),
    input_tokens DOUBLE PRECISION,
    output_tokens DOUBLE PRECISION
);

COMMENT ON TABLE model_call_logs IS '大模型API接口调用历史日志明细表';
COMMENT ON COLUMN model_call_logs.id IS '自增主键ID';
COMMENT ON COLUMN model_call_logs.task_id IS '关联的环境对比任务ID';
COMMENT ON COLUMN model_call_logs.task_name IS '关联的环境对比任务名称';
COMMENT ON COLUMN model_call_logs.model_id IS '被调用的AI模型ID';
COMMENT ON COLUMN model_call_logs.model_url IS '请求的完整API endpoint URL';
COMMENT ON COLUMN model_call_logs.request_payload IS '发送给大模型的完整原始请求JSON文本';
COMMENT ON COLUMN model_call_logs.response_body IS '大模型返回的完整原始响应JSON文本';
COMMENT ON COLUMN model_call_logs.started_at IS '请求调用的发起时间';
COMMENT ON COLUMN model_call_logs.ended_at IS '请求收到响应的结束时间';
COMMENT ON COLUMN model_call_logs.status_code IS 'HTTP响应状态码，例如 200, 400, 500';
COMMENT ON COLUMN model_call_logs.input_tokens IS '当前单次请求所消耗的输入Token数量';
COMMENT ON COLUMN model_call_logs.output_tokens IS '当前单次请求所消耗的输出Token数量';


-- -----------------------------------------------------------------------------
-- 6. 索引设计 (以提升常用查询、过滤和关联性能)
-- -----------------------------------------------------------------------------

-- 对任务状态、模型ID以及创建时间进行索引，便于列表查询和过滤
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_tasks_model_id ON tasks(model_id);
CREATE INDEX IF NOT EXISTS idx_tasks_created_at ON tasks(created_at DESC);

-- 调用日志表经常按任务ID和模型ID以及调用时间查询
CREATE INDEX IF NOT EXISTS idx_call_logs_task_id ON model_call_logs(task_id);
CREATE INDEX IF NOT EXISTS idx_call_logs_model_id ON model_call_logs(model_id);
CREATE INDEX IF NOT EXISTS idx_call_logs_started_at ON model_call_logs(started_at DESC);

-- 模型配置表按排序权重、是否默认字段进行索引
CREATE INDEX IF NOT EXISTS idx_ai_models_is_default ON ai_models(is_default);
CREATE INDEX IF NOT EXISTS idx_ai_models_sort_order ON ai_models(sort_order);
