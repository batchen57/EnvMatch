package com.envmatch.service;

import com.envmatch.model.AIModel;
import com.envmatch.model.PromptTemplate;
import com.envmatch.mapper.AIModelMapper;
import com.envmatch.mapper.PromptTemplateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据库基础种子数据初始化服务。
 * 
 * <p>负责在系统运行期间（如访问模型列表或提示词模板列表时）进行防空检测。
 * 如果发现对应数据库表为空，则会自动注入默认的 AI 模型配置（包括 Gemini 2.5 Pro、GPT-4o、MiniMax-M2.7 等）
 * 以及系统内置的提示词模板（如系统默认通用提示词、反欺诈中介环境对比专用提示词），以保障平台的开箱即用能力。</p>
 */
@Service
public class SeedService {
    
    /** AI模型配置映射接口 */
    private final AIModelMapper modelMapper;
    
    /** 提示词模板映射接口 */
    private final PromptTemplateMapper promptMapper;
    
    /** Jackson 对象转换器 */
    private final ObjectMapper mapper;

    /**
     * 构造函数，由 Spring 容器自动注入依赖。
     *
     * @param modelMapper  模型 Mapper
     * @param promptMapper 模板 Mapper
     * @param mapper       ObjectMapper
     */
    public SeedService(AIModelMapper modelMapper, PromptTemplateMapper promptMapper, ObjectMapper mapper) {
        this.modelMapper = modelMapper;
        this.promptMapper = promptMapper;
        this.mapper = mapper;
    }

    /**
     * 检测并确保模型表（ai_models）中含有基本种子数据。
     * 如果表为空，则预置注入 Google Gemini、OpenAI GPT-4o、Alibaba 通义千问以及 MiniMax 多模态模型配置。
     */
    public void ensureModels() {
        if (modelMapper.selectCount(null) > 0) return;
        addModel("Gemini 2.5 Pro", "gemini-2.5-pro", "Google", "", "",
                "多模态理解能力强，适合复杂场景 and 环境细节分析。", List.of("text", "image", "video"), "true", 0);
        addModel("GPT-4o", "gpt-4o", "OpenAI", "", "https://api.openai.com/v1",
                "通用性强，稳定可靠。", List.of("text", "image", "video"), "false", 10);
        addModel("MiniMax-M2.7", "MiniMax-M2.7", "MiniMax", "", "https://api.minimaxi.com/v1/coding_plan/vlm",
                "国产多模态模型，支持图片理解与环境分析。", List.of("text", "image"), "false", 20);
        addModel("MiniMax-M3", "MiniMax-M3", "MiniMax", "", "https://api.minimaxi.com/v1",
                "MiniMax 最新旗舰，原生多模态，100 万 Token 上下文，反欺诈环境对比推荐。", List.of("text", "image", "video"), "false", 25);
        addModel("Qwen3-VL-Plus", "qwen-vl-plus", "Alibaba", "", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "通义千问视觉模型，支持图片与视频帧理解。", List.of("text", "image", "video"), "false", 40);
    }

    /**
     * 检测并确保提示词模板表（prompt_templates）中含有内置种子数据。
     * 如果表为空，则预置创建“系统默认通用提示词”和“反欺诈中介环境对比专用提示词”。
     */
    public void ensurePrompts() {
        if (promptMapper.selectCount(null) > 0) return;

        PromptTemplate defaultTemplate = new PromptTemplate();
        defaultTemplate.setName("系统默认通用提示词");
        defaultTemplate.setContent("""
                你是一个专业的环境场景分析师。我会提供两段视频或关键帧，请忽略主体人物、动作和前景物体，将注意力集中在背景环境上。
                请从室内/室外属性、天气光线、地貌植被、建筑风格、固定设施、地面材质等维度比较两个环境的相似度。
                返回 JSON，包含 similarity_score、dimension_scores、similar_points、difference_points 和 summary。
                """);
        promptMapper.insert(defaultTemplate);

        PromptTemplate antifraudTemplate = new PromptTemplate();
        antifraudTemplate.setName("反欺诈中介环境对比专用");
        antifraudTemplate.setContent("""
                你是一名反欺诈调查中的环境鉴定专家。你的任务是判断两段视频/关键帧是否拍摄于同一个物理空间（如同一间房屋、门店、办公室），以识别中介团伙反复使用同一场地进行虚假申请的行为。
 
                【核心原则】
                1. 完全忽略画面中的人物、手持物品、手机/电脑屏幕内容、临时摆放的文件等前景干扰。
                2. 聚焦不可移动或难以短期更改的固定环境特征。
                3. 对以下高价值特征给予特别关注：
                   - 墙面颜色/纹理/壁纸花纹、踢脚线样式
                   - 地板/地砖材质、纹理、拼接方式
                   - 门窗框架样式、把手造型、窗帘轨道
                   - 天花板造型、吊灯/灯具款式与位置
                   - 开关面板/插座位置与型号
                   - 固定家具（橱柜、嵌入式衣柜、固定书架）
                   - 空间布局与房间结构（门的相对位置、走廊方向、窗户朝向）
 
                【评分维度】
                - indoor_layout: 室内空间布局与结构一致性
                - wall_floor_material: 墙面/地面材质与颜色一致性
                - furniture_fixtures: 固定家具与设施一致性
                - window_door_style: 门窗样式与位置一致性
                - lighting_environment: 灯具类型、光照环境一致性
 
                返回严格 JSON，不要包含 Markdown 或解释性文字。
                """);
        promptMapper.insert(antifraudTemplate);
    }

    /**
     * 辅助方法：向数据库持久化添加一个新模型配置。
     *
     * @param name         模型名称
     * @param identifier   模型标识符
     * @param provider     模型供应商
     * @param key          API Key
     * @param url          API 调用端点 Base URL
     * @param description  模型描述
     * @param capabilities 支持的能力列表
     * @param isDefault    是否默认
     * @param sortOrder    排序权重
     */
    private void addModel(String name, String identifier, String provider, String key, String url,
                          String description, List<String> capabilities, String isDefault, double sortOrder) {
        AIModel model = new AIModel();
        model.setName(name);
        model.setIdentifier(identifier);
        model.setProvider(provider);
        model.setApiKey(key);
        model.setBaseUrl(url);
        model.setDescription(description);
        ArrayNode caps = mapper.createArrayNode();
        capabilities.forEach(caps::add);
        model.setCapabilities(caps);
        model.setIsDefault(isDefault);
        model.setSortOrder(sortOrder);
        modelMapper.insert(model);
    }
}
