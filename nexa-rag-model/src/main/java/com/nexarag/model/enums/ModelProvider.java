package com.nexarag.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/**
 * 模型服务提供方。
 */
public enum ModelProvider {

    /**
     * OpenAI 官方服务。
     */
    OPENAI(true),

    /**
     * Ollama 本地或远程服务。
     */
    OLLAMA(true),

    /**
     * 阿里云 DashScope 服务。
     */
    DASHSCOPE(true),

    /**
     * DeepSeek 服务。
     */
    DEEPSEEK(true),

    /**
     * SiliconFlow 服务。
     */
    SILICONFLOW(true),

    /**
     * 智谱 AI 服务。
     */
    ZHIPU(true),

    /**
     * Moonshot 服务。
     */
    MOONSHOT(true),

    /**
     * 自定义 OpenAI 兼容服务。
     */
    CUSTOM_OPENAI(true);

    private final boolean openAiCompatible;

    ModelProvider(boolean openAiCompatible) {
        this.openAiCompatible = openAiCompatible;
    }

    /**
     * 解析模型厂商，兼容更新请求中的空字符串占位。
     *
     * @param value 请求中的模型厂商
     * @return 模型厂商，空字符串返回 null
     */
    @JsonCreator
    public static ModelProvider fromJson(String value) {
        // 1. 空字符串表示未传该字段，交由更新逻辑忽略
        if (value == null || value.isBlank()) {
            return null;
        }

        // 2. 兼容历史 OpenAI 协议配置名称，统一映射为当前枚举值
        String normalizedValue = value.trim().toUpperCase(Locale.ROOT);
        if ("OPENAI_COMPATIBLE".equals(normalizedValue)) {
            return CUSTOM_OPENAI;
        }

        // 3. 非空值按枚举名称解析
        return ModelProvider.valueOf(normalizedValue);
    }

    /**
     * 判断当前厂商是否支持 OpenAI 兼容调用协议。
     *
     * @return 支持返回 true，否则返回 false
     */
    public boolean isOpenAiCompatible() {
        // 1. 统一由枚举维护厂商能力，避免各 Provider 分散维护白名单
        return openAiCompatible;
    }
}
