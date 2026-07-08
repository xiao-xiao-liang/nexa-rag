package com.nexarag.retrieval.index.vector;

import com.nexarag.retrieval.dto.VectorIndexWriteRequest;
import com.nexarag.retrieval.model.VectorIndexWriteResult;

import java.util.List;

/**
 * 向量索引客户端，抽象向量索引写入和按文档清理能力。
 */
public interface VectorIndexClient {

    /**
     * 批量写入或更新向量索引。
     *
     * @param request 向量索引写入请求
     * @return 写入结果列表
     */
    List<VectorIndexWriteResult> upsert(VectorIndexWriteRequest request);

    /**
     * 按文档ID删除向量索引。
     *
     * @param documentId 文档ID
     * @return 删除数量
     */
    int deleteByDocumentId(Long documentId);
}