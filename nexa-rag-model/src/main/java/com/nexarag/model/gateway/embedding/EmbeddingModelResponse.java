package com.nexarag.model.gateway.embedding;

import java.util.List;

/**
 * Embedding 模型响应。
 *
 * @param embeddings    向量结果
 * @param modelProfile  实际使用的模型Profile
 * @param totalTokens   总Token数量
 */
public record EmbeddingModelResponse(List<float[]> embeddings, String modelProfile, Integer totalTokens) {
}
