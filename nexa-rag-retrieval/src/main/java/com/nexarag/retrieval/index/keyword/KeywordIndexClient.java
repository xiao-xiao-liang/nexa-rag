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
     * 按文档版本替换关键词索引，保留同一文档的其他历史版本。
     *
     * @param request 关键词索引替换请求
     * @return 写入结果列表
     */
    default List<KeywordIndexWriteResult> replaceDocumentVersion(KeywordIndexWriteRequest request) {
        if (request == null || request.documentId() == null || request.documentVersionId() == null) {
            throw new IllegalArgumentException("关键词索引版本替换请求、文档ID或文档版本ID不能为空");
        }

        // 1. 先清理当前版本的旧关键词记录
        deleteByDocumentVersionId(request.documentId(), request.documentVersionId(), request.indexName());

        // 2. 写入本次版本处理生成的新关键词片段
        return upsert(request);
    }

    /**
     * 按文档和版本ID删除指定关键词索引中的记录。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @param indexName         索引名称，为空时使用默认正文索引
     * @return 删除数量
     */
    default int deleteByDocumentVersionId(Long documentId, Long documentVersionId, String indexName) {
        throw new UnsupportedOperationException("当前关键词索引不支持按文档版本删除");
    }
}
