package com.nexarag.retrieval.dto;

import com.nexarag.retrieval.model.VectorIndexDocument;

import java.util.List;

/**
 * 向量索引写入请求。
 *
 * @param collectionName 向量集合名称
 * @param documentId     文档ID
 * @param documents      待写入向量文档
 */
public record VectorIndexWriteRequest(String collectionName,
                                      Long documentId,
                                      List<VectorIndexDocument> documents) {
}