package com.nexarag.retrieval.model;

/**
 * 关键词索引文档，用于承载片段文本和元数据。
 *
 * @param chunkId       片段ID
 * @param documentId    文档ID
 * @param parentChunkId 父片段ID
 * @param chunkOrder    片段顺序
 * @param text          片段文本
 * @param metadataJson  元数据JSON
 */
public record KeywordIndexDocument(String chunkId,
                                   Long documentId,
                                   String parentChunkId,
                                   Integer chunkOrder,
                                   String text,
                                   String metadataJson) {
}