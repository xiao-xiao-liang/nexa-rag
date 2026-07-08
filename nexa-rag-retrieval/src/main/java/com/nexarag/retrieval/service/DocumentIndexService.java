package com.nexarag.retrieval.service;

import com.nexarag.retrieval.dto.DocumentIndexCleanupResult;
import com.nexarag.retrieval.dto.DocumentIndexResult;

/**
 * 文档索引服务，对 Workflow 暴露文档索引写入和索引清理入口。
 */
public interface DocumentIndexService {

    /**
     * 执行指定文档的索引写入。
     *
     * @param documentId 文档ID
     * @return 文档索引结果
     */
    DocumentIndexResult indexDocument(Long documentId);

    /**
     * 清理指定文档的外部索引。
     *
     * @param documentId 文档ID
     * @return 文档索引清理结果
     */
    DocumentIndexCleanupResult cleanupDocumentIndex(Long documentId);
}