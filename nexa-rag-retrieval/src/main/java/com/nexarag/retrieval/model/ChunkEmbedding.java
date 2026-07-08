package com.nexarag.retrieval.model;

/**
 * 片段向量化结果，用于把片段ID与 Embedding 向量绑定起来。
 *
 * @param chunkId      片段ID
 * @param vector       向量数据
 * @param modelProfile 模型配置标识
 * @param tokenCount   Token数量
 */
public record ChunkEmbedding(String chunkId, float[] vector, String modelProfile, Integer tokenCount) {
}