package com.nexarag.retrieval.service.impl;

import com.nexarag.retrieval.dto.DocumentIndexCleanupResult;
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
        // 1. 先清理向量索引
        int vectorDeletedCount = vectorIndexClient.deleteByDocumentId(documentId);

        // 2. 再清理关键词索引
        int keywordDeletedCount = keywordIndexClient.deleteByDocumentId(documentId);

        // 3. 返回清理统计
        return new DocumentIndexCleanupResult(documentId, vectorDeletedCount, keywordDeletedCount, true, null);
    }
}