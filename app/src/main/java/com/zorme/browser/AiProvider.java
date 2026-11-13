package com.zorme.browser;

/**
 * OpenAI 兼容的 AI 服务商预设。
 * 所有预设均遵循 OpenAI Chat Completions 协议（POST {base}/chat/completions）。
 */
public final class AiProvider {

    public final String name;
    public final String baseUrl;
    public final String model;
    public final String note;

    public AiProvider(String name, String baseUrl, String model, String note) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.model = model;
        this.note = note;
    }

    public static final AiProvider[] PRESETS = {
            new AiProvider("智谱", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash",
                    "在 open.bigmodel.cn 创建 API Key；glm-4-flash 免费可用"),
            new AiProvider("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat",
                    "在 platform.deepseek.com 创建 API Key；deepseek-chat 对话 / deepseek-reasoner 深度推理"),
            new AiProvider("阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus",
                    "在阿里云百炼控制台创建 API Key；通义千问 qwen-plus / qwen-turbo / qwen-max"),
            new AiProvider("基元律动", "https://tokenrhythm.studio/v1", "deepseek-v4-flash-0731",
                    "多模型聚合平台 tokenrhythm.studio，一个 Key 可调用多家模型"),
            new AiProvider("字节跳动", "https://ark.cn-beijing.volces.com/api/v3", "doubao-seed-1-6-lite-250715",
                    "在火山方舟控制台创建 API Key；模型填模型 ID 或接入点 ep-xxx"),
            new AiProvider("自定义", "", "",
                    "任意 OpenAI 兼容接口：自行填写 API 地址、Key 与模型名称"),
    };

    public static final int CUSTOM = PRESETS.length - 1;

    /** 按名称查找预设，找不到返回 null */
    public static AiProvider byName(String name) {
        if (name == null) return null;
        for (AiProvider p : PRESETS) {
            if (p.name.equals(name)) return p;
        }
        return null;
    }

    /** 当前生效的服务商显示名（地址与预设不符则视为自定义） */
    public static String currentName(String base, String savedName) {
        if (base == null || base.trim().isEmpty()) return PRESETS[CUSTOM].name;
        for (AiProvider p : PRESETS) {
            if (p.baseUrl.equals(base.trim())) return p.name;
        }
        return PRESETS[CUSTOM].name;
    }
}
