package com.nexarag.model.enums;

/**
 * 模型服务提供方。
 */
public enum ModelProvider {

    /**
     * OpenAI 官方服务。
     */
    OPENAI,

    /**
     * Ollama 本地或远程服务。
     */
    OLLAMA,

    /**
     * 阿里云 DashScope 服务。
     */
    DASHSCOPE,

    /**
     * DeepSeek 服务。
     */
    DEEPSEEK,

    /**
     * SiliconFlow 服务。
     */
    SILICONFLOW,

    /**
     * 智谱 AI 服务。
     */
    ZHIPU,

    /**
     * Moonshot 服务。
     */
    MOONSHOT,

    /**
     * 自定义 OpenAI 兼容服务。
     */
    CUSTOM_OPENAI
}
