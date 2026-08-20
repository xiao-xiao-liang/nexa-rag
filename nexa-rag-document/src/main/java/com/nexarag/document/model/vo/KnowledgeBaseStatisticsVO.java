package com.nexarag.document.model.vo;

/**
 * 知识库内文档处理状态统计展示对象。
 *
 * @param totalCount 文档总数
 * @param pendingCount 待处理文档数
 * @param processingCount 处理中文档数
 * @param indexedCount 已索引文档数
 * @param failedCount 失败文档数
 */
public record KnowledgeBaseStatisticsVO(long totalCount, long pendingCount, long processingCount,
                                        long indexedCount, long failedCount) {
}
