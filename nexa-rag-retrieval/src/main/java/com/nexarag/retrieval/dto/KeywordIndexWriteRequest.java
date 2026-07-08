package com.nexarag.retrieval.dto;

import com.nexarag.retrieval.model.KeywordIndexDocument;

import java.util.List;

/**
 * 关键词索引写入请求。
 *
 * @param indexName  关键词索引名称
 * @param documentId 文档ID
 * @param documents  待写入关键词文档
 */
public record KeywordIndexWriteRequest(String indexName,
                                       Long documentId,
                                       List<KeywordIndexDocument> documents) {
}