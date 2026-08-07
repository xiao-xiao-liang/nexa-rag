package com.nexarag.retrieval.model;

/**
 * 向量索引文档，用于承载片段文本、元数据和向量。
 *
 * @param chunkId       片段ID
 * @param documentId    文档ID
 * @param parentChunkId 父片段ID
 * @param chunkOrder    片段顺序
 * @param text          片段文本
 * @param metadataJson  元数据JSON
 * @param vector        向量数据
 */
public record VectorIndexDocument(String chunkId,
                                  Long documentId,
                                  String parentChunkId,
                                  Integer chunkOrder,
                                  String text,
                                  String metadataJson,
                                  float[] vector) {
}