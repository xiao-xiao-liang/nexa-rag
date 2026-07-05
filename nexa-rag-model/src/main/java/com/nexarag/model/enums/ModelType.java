package com.nexarag.model.enums;

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
    RERANK
}
