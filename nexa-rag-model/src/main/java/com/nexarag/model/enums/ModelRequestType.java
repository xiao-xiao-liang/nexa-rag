package com.nexarag.model.enums;

/**
 * 模型请求类型。
 */
public enum ModelRequestType {

    /**
     * 聊天请求。
     */
    CHAT,

    /**
     * 向量化请求。
     */
    EMBEDDING,

    /**
     * 重排序请求。
     */
    RERANK,

    /**
     * 视觉模型请求。
     */
    VISION,

    /**
     * 向量化模型连接测试请求。
     */
    EMBEDDING_TEST,

    /**
     * 重排序模型连接测试请求。
     */
    RERANK_TEST,

    /**
     * 聊天模型连接测试请求。
     */
    CHAT_TEST
}
