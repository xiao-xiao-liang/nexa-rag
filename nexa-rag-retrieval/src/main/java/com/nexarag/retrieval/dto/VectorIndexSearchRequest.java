package com.nexarag.retrieval.dto;

/**
 * 向量索引检索请求。
 *
 * @param collectionName 集合名称
 * @param vector 查询向量
 * @param topK 返回数量
 */
public record VectorIndexSearchRequest(String collectionName, float[] vector, int topK) {
}
