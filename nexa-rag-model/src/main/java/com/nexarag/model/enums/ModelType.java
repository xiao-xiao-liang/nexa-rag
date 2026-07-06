package com.nexarag.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/**
 * 模型类型枚举，用于区分聊天、向量化和重排序模型。
 */
public enum ModelType {

    /**
     * 聊天模型。
     */
    CHAT,

    /**
     * 向量化模型。
     */
    EMBEDDING,

    /**
     * 重排序模型。
     */
    RERANK;

    /**
     * 解析模型类型，兼容更新请求中的空字符串占位。
     *
     * @param value 请求中的模型类型
     * @return 模型类型，空字符串返回 null
     */
    @JsonCreator
    public static ModelType fromJson(String value) {
        // 1. 空字符串表示未传该字段，交由更新逻辑忽略
        if (value == null || value.isBlank()) {
            return null;
        }

        // 2. 非空值按枚举名称解析
        return ModelType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
