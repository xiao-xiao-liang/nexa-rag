package com.nexarag.retrieval.model;

/**
 * 向量索引写入结果。
 *
 * @param chunkId       片段ID
 * @param vectorId      向量索引ID
 * @param success       是否成功
 * @param failureReason 失败原因
 */
public record VectorIndexWriteResult(String chunkId, String vectorId, boolean success, String failureReason) {
}