package com.nexarag.retrieval.model;

/**
 * 章节范围内的正文片段，用于将导航命中的章节转换为可回答的原始正文证据。
 *
 * @param chunkId    片段标识
 * @param documentId 文档标识
 * @param sectionId  所属章节标识
 * @param content    原始正文
 * @param tokenCount 已持久化的 Token 数量
 */
public record SectionContentChunk(String chunkId, Long documentId, Long documentVersionId, Long sectionId,
                                  String content,
                                  Integer tokenCount) {
    public SectionContentChunk(String chunkId, Long documentId, Long sectionId, String content, Integer tokenCount) {
        this(chunkId, documentId, null, sectionId, content, tokenCount);
    }
}
