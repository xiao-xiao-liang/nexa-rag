package com.nexarag.retrieval.dto.req;

import com.nexarag.retrieval.model.KeywordIndexDocument;

import java.util.List;

/**
 * 关键词索引写入请求。
 *
 * @param indexName         关键词索引名称
 * @param documentId        文档ID
 * @param documentVersionId 文档版本ID
 * @param documents         待写入关键词文档
 */
public record KeywordIndexWriteRequest(
        String indexName,
        Long documentId,
        Long documentVersionId,
        List<KeywordIndexDocument> documents
) {

    /**
     * 兼容未引入文档版本的既有关键词索引写入请求。
     */
    public KeywordIndexWriteRequest(String indexName, Long documentId, List<KeywordIndexDocument> documents) {
        this(indexName, documentId, null, documents);
    }
}
