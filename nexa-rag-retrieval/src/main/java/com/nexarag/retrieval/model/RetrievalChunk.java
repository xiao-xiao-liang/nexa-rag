package com.nexarag.retrieval.model;

/**
 * 标准化的对话检索片段。
 *
 * @param chunkId       片段标识
 * @param documentId    文档标识
 * @param chunkIndex    文档内片段序号
 * @param parentChunkId 父片段标识
 * @param title         文档标题
 * @param source        文档来源
 * @param content       片段正文
 * @param score         通道原始分数
 * @param channel       召回通道
 * @param rank          通道内名次
 */
public record RetrievalChunk(String chunkId, Long documentId, Integer chunkIndex, String parentChunkId,
                             String title, String source, String content, double score, String channel, int rank,
                             Long documentVersionId) {
    public RetrievalChunk(String chunkId, Long documentId, Integer chunkIndex, String parentChunkId,
                          String title, String source, String content, double score, String channel, int rank) {
        this(chunkId, documentId, chunkIndex, parentChunkId, title, source, content, score, channel, rank, null);
    }
}
