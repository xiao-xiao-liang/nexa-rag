package com.nexarag.retrieval.model;

/**
 * 关键词索引写入结果。
 *
 * @param chunkId        片段ID
 * @param keywordIndexId 关键词索引ID
 * @param success        是否成功
 * @param failureReason  失败原因
 */
public record KeywordIndexWriteResult(String chunkId, String keywordIndexId, boolean success, String failureReason) {
}