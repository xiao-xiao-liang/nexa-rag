package com.nexarag.retrieval.service;

import com.nexarag.retrieval.dto.res.DocumentIndexCleanupResult;

/**
 * 文档索引清理器，封装文档索引在不同索引介质中的删除顺序。
 */
public interface DocumentIndexCleaner {

    /**
     * 清理指定文档的外部索引。
     *
     * @param documentId 文档ID
     * @return 索引清理结果
     */
    DocumentIndexCleanupResult cleanup(Long documentId);
}