package com.envmatch.service;

import com.envmatch.model.AIModel;
import com.envmatch.model.PromptTemplate;
import com.envmatch.repository.AIModelRepository;
import com.envmatch.repository.PromptTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeedService {
    private final AIModelRepository modelRepository;
    private final PromptTemplateRepository promptRepository;
    private final ObjectMapper mapper;

    public SeedService(AIModelRepository modelRepository, PromptTemplateRepository promptRepository, ObjectMapper mapper) {
        this.modelRepository = modelRepository;
        this.promptRepository = promptRepository;
        this.mapper = mapper;
    }

    public void ensureModels() {
        if (modelRepository.count() > 0) return;
        addModel("Gemini 2.5 Pro", "gemini-2.5-pro", "Google", "", "",
                "多模态理解能力强，适合复杂场景和环境细节分析。", List.of("text", "image", "video"), "true", 0);
        addModel("GPT-4o", "gpt-4o", "OpenAI", "", "https://api.openai.com/v1",
                "通用性强，稳定可靠。", List.of("text", "image", "video"), "false", 10);
        addModel("MiniMax 2.7", "minimax-2.7", "MiniMax", "", "https://api.minimax.chat/v1",
                "国产多模态模型。", List.of("text", "image"), "false", 20);
        addModel("MiniMax-M2.7 VLM", "MiniMax-M2.7", "MiniMax", "", "https://api.minimaxi.com/v1/coding_plan/vlm",
                "MiniMax 专用 VLM Endpoint。", List.of("text", "image"), "false", 30);
        addModel("Qwen3-VL-Plus", "qwen-vl-plus", "Alibaba", "", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "通义千问视觉模型，支持图片与视频帧理解。", List.of("text", "image", "video"), "false", 40);
    }

    public void ensurePrompts() {
        if (promptRepository.count() > 0) return;
        PromptTemplate template = new PromptTemplate();
        template.setName("系统默认通用提示词");
        template.setContent("""
                你是一个专业的环境场景分析师。我会提供两段视频或关键帧，请忽略主体人物、动作和前景物体，将注意力集中在背景环境上。
                请从室内/室外属性、天气光线、地貌植被、建筑风格、固定设施、地面材质等维度比较两个环境的相似度。
                返回 JSON，包含 similarity_score、dimension_scores、similar_points、difference_points 和 summary。
                """);
        promptRepository.save(template);
    }

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
        modelRepository.save(model);
    }
}
