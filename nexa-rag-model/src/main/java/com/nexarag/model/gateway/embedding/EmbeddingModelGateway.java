package com.nexarag.model.gateway.embedding;

/**
 * Embedding 模型网关。
 */
public interface EmbeddingModelGateway {

    /**
     * 调用 Embedding 模型。
     *
     * @param request Embedding 模型请求
     * @return Embedding 模型响应
     */
    EmbeddingModelResponse embed(EmbeddingModelRequest request);
}
