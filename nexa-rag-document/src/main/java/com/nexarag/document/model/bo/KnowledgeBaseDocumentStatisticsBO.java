package com.nexarag.document.model.bo;

/**
 * 知识库当前生效版本的文档状态聚合结果。
 *
 * @param knowledgeBaseId 知识库ID
 * @param totalCount 文档总数
 * @param pendingCount 待处理文档数
 * @param processingCount 处理中文档数
 * @param indexedCount 已索引文档数
 * @param failedCount 失败文档数
 */
public record KnowledgeBaseDocumentStatisticsBO(Long knowledgeBaseId, long totalCount, long pendingCount,
                                                 long processingCount, long indexedCount, long failedCount) {
}
