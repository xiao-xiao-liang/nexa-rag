package com.nexarag.retrieval.dto;

/**
 * 单个文档片段索引结果，用于描述片段是否写入索引以及对应的索引ID。
 *
 * @param chunkId        片段ID
 * @param success        是否成功
 * @param skipped        是否跳过索引
 * @param vectorId       向量索引ID
 * @param keywordIndexId 关键词索引ID
 * @param failureReason  失败原因
 */
public record DocumentChunkIndexResult(String chunkId,
                                       boolean success,
                                       boolean skipped,
                                       String vectorId,
                                       String keywordIndexId,
                                       String failureReason) {
}