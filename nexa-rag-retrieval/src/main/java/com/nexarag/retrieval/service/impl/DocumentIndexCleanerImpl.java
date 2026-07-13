package com.nexarag.retrieval.service.impl;

import com.nexarag.retrieval.dto.res.DocumentIndexCleanupResult;
import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.index.vector.VectorIndexClient;
import com.nexarag.retrieval.service.DocumentIndexCleaner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 文档索引清理器实现，按向量索引、关键词索引的顺序执行清理。
 */
@Component
@RequiredArgsConstructor
public class DocumentIndexCleanerImpl implements DocumentIndexCleaner {

    private final VectorIndexClient vectorIndexClient;
    private final KeywordIndexClient keywordIndexClient;

    /**
     * 清理指定文档的外部索引。
     *
     * @param documentId 文档ID
     * @return 索引清理结果
     */
    @Override
    public DocumentIndexCleanupResult cleanup(Long documentId) {
        int vectorDeletedCount = 0;
        int keywordDeletedCount = 0;
        String vectorFailure = null;
        String keywordFailure = null;

        // 1. 独立清理向量索引，失败后仍继续执行关键词索引清理
        try {
            vectorDeletedCount = vectorIndexClient.deleteByDocumentId(documentId);
        } catch (RuntimeException exception) {
            vectorFailure = "向量索引清理失败：" + exception.getMessage();
        }

        // 2. 独立清理关键词索引，保留向量索引清理结果
        try {
            keywordDeletedCount = keywordIndexClient.deleteByDocumentId(documentId);
        } catch (RuntimeException exception) {
            keywordFailure = "关键词索引清理失败：" + exception.getMessage();
        }

        // 3. 聚合两个阶段的删除数量和失败原因
        String failureReason = java.util.stream.Stream.of(vectorFailure, keywordFailure)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("；"));
        return new DocumentIndexCleanupResult(documentId, vectorDeletedCount, keywordDeletedCount,
                failureReason.isEmpty(), failureReason.isEmpty() ? null : failureReason);
    }
}
