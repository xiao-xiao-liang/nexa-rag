package com.nexarag.retrieval.index.keyword;

import com.nexarag.retrieval.dto.KeywordIndexSearchRequest;
import com.nexarag.retrieval.dto.KeywordIndexWriteRequest;
import com.nexarag.retrieval.model.KeywordIndexSearchResult;
import com.nexarag.retrieval.model.KeywordIndexWriteResult;

import java.util.List;

/**
 * 关键词索引客户端，抽象关键词索引写入和按文档清理能力。
 */
public interface KeywordIndexClient {

    /**
     * 按关键词检索片段。
     *
     * @param request 关键词检索请求
     * @return 按相关性排序的片段结果
     */
    default List<KeywordIndexSearchResult> search(KeywordIndexSearchRequest request) {
        return List.of();
    }

    /**
     * 批量写入或更新关键词索引。
     *
     * @param request 关键词索引写入请求
     * @return 写入结果列表
     */
    List<KeywordIndexWriteResult> upsert(KeywordIndexWriteRequest request);

    /**
     * 按文档ID删除关键词索引。
     *
     * @param documentId 文档ID
     * @return 删除数量
     */
    int deleteByDocumentId(Long documentId);
}
