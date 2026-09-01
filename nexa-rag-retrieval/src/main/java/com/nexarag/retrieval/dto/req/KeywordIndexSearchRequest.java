package com.nexarag.retrieval.dto.req;

import java.util.Set;

/**
 * 关键词索引检索请求。
 *
 * @param indexName 索引名称
 * @param query     查询文本
 * @param topK      返回数量
 */
public record KeywordIndexSearchRequest(String indexName, String query, int topK, Set<Long> activeVersionIds) {
    public KeywordIndexSearchRequest(String indexName, String query, int topK) {
        this(indexName, query, topK, Set.of());
    }
}
