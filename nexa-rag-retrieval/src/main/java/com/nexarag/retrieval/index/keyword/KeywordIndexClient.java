package com.nexarag.retrieval.index.keyword;

import com.nexarag.retrieval.dto.req.KeywordIndexSearchRequest;
import com.nexarag.retrieval.dto.req.KeywordIndexWriteRequest;
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
     * 按文档替换指定关键词索引中的全部正文记录。
     *
     * <p>文档重处理会生成新的 chunkId，必须先清除旧记录再写入新片段，
     * 避免 Elasticsearch 保留已失效的正文片段。</p>
     *
     * @param request 关键词索引替换请求
     * @return 写入结果列表
     */
    default List<KeywordIndexWriteResult> replaceDocument(KeywordIndexWriteRequest request) {
        if (request == null || request.documentId() == null) {
            throw new IllegalArgumentException("关键词索引替换请求或文档ID不能为空");
        }

        // 1. 先清理当前文档的旧关键词记录
        deleteByDocumentId(request.documentId(), request.indexName());

        // 2. 写入本次切分生成的新关键词片段
        return upsert(request);
    }

    /**
     * 按文档ID删除关键词索引。
     *
     * @param documentId 文档ID
     * @return 删除数量
     */
    int deleteByDocumentId(Long documentId);

    /**
     * 按文档ID删除指定关键词索引中的记录。
     *
     * @param documentId 文档ID
     * @param indexName  索引名称，为空时使用默认正文索引
     * @return 删除数量
     */
    default int deleteByDocumentId(Long documentId, String indexName) {
        return deleteByDocumentId(documentId);
    }
}
