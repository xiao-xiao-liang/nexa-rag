package com.nexarag.retrieval.model;

/**
 * 向量索引检索结果。
 *
 * @param chunkId 片段标识
 * @param documentId 文档标识
 * @param parentChunkId 父片段标识
 * @param chunkOrder 片段序号
 * @param text 片段正文
 * @param metadataJson 元数据 JSON
 * @param score 向量相似度分数
 */
public record VectorIndexSearchResult(String chunkId, Long documentId, String parentChunkId, Integer chunkOrder,
                                      String text, String metadataJson, double score) {
}
