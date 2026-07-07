package com.nexarag.model.enums;

/**
 * Token 用量统计来源。
 */
public enum TokenUsageSource {

    /**
     * 厂商响应中的 usage 字段。
     */
    PROVIDER_USAGE,

    /**
     * 厂商官方规则计算。
     */
    PROVIDER_RULE,

    /**
     * 本地 tokenizer 计算。
     */
    LOCAL_TOKENIZER,

    /**
     * 近似估算。
     */
    ESTIMATED,

    /**
     * 暂无法统计。
     */
    UNKNOWN
}
