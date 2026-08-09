package com.nexarag.retrieval.dto.res;

/**
 * 文档索引清理结果，用于描述向量索引和关键词索引的删除统计。
 *
 * @param documentId           文档ID
 * @param vectorDeletedCount   向量索引预计删除数量，以关系库已索引片段数统计
 * @param keywordDeletedCount  关键词索引删除数量
 * @param success              是否清理成功
 * @param failureReason        失败原因
 */
public record DocumentIndexCleanupResult(Long documentId,
                                         int vectorDeletedCount,
                                         int keywordDeletedCount,
                                         boolean success,
                                         String failureReason) {
}