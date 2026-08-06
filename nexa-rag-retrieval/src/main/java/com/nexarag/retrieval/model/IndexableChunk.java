package com.nexarag.retrieval.model;

import lombok.Builder;

/**
 * 可索引片段快照，用于隔离 retrieval 模块内部处理与 document 实体直接修改。
 *
 * @param chunkId       片段ID
 * @param documentId    文档ID
 * @param chunkOrder    片段顺序
 * @param parentChunkId 父片段ID
 * @param sectionId     所属章节ID
 * @param text          片段文本
 * @param indexContent  用于索引的片段内容
 * @param metadataJson  元数据JSON
 * @param tokenCount    Token数量
 */
@Builder
public record IndexableChunk(String chunkId,
                             Long documentId,
                             Integer chunkOrder,
                             String parentChunkId,
                             Long sectionId,
                             String text,
                             String indexContent,
                             String metadataJson,
                             Integer tokenCount) {

    /**
     * 兼容尚未写入章节和索引内容的历史片段构造方式。
     */
    public IndexableChunk(String chunkId,
                          Long documentId,
                          Integer chunkOrder,
                          String parentChunkId,
                          String text,
                          String metadataJson,
                          Integer tokenCount) {
        this(chunkId, documentId, chunkOrder, parentChunkId, null, text, text, metadataJson, tokenCount);
    }
}
