package com.nexarag.retrieval.model;

/**
 * 关键词索引检索结果。
 *
 * @param chunkId 片段标识
 * @param documentId 文档标识
 * @param parentChunkId 父片段标识
 * @param chunkOrder 片段序号
 * @param sectionId 所属章节标识
 * @param text 片段正文
 * @param metadataJson 元数据 JSON
 * @param score BM25 分数
 */
public record KeywordIndexSearchResult(String chunkId, Long documentId, String parentChunkId, Integer chunkOrder,
                                       Long sectionId, String text, String metadataJson, double score) {

    /**
     * 兼容既有关键词索引检索结果构造方式。
     */
    public KeywordIndexSearchResult(String chunkId, Long documentId, String parentChunkId, Integer chunkOrder,
                                    String text, String metadataJson, double score) {
        this(chunkId, documentId, parentChunkId, chunkOrder, null, text, metadataJson, score);
    }
}
