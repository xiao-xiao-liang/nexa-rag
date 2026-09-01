package com.nexarag.retrieval.model;

/**
 * 向量索引检索结果。
 *
 * @param chunkId           片段标识
 * @param documentId        文档标识
 * @param documentVersionId 文档版本标识
 * @param parentChunkId     父片段标识
 * @param chunkOrder        片段序号
 * @param sectionId         所属章节标识
 * @param text              片段正文
 * @param metadataJson      元数据 JSON
 * @param score             向量相似度分数
 */
public record VectorIndexSearchResult(String chunkId, Long documentId, Long documentVersionId,
                                      String parentChunkId, Integer chunkOrder,
                                      Long sectionId, String text, String metadataJson, double score) {

    /**
     * 兼容既有向量索引检索结果构造方式。
     */
    public VectorIndexSearchResult(String chunkId, Long documentId, String parentChunkId, Integer chunkOrder,
                                   String text, String metadataJson, double score) {
        this(chunkId, documentId, null, parentChunkId, chunkOrder, null, text, metadataJson, score);
    }

    /**
     * 兼容未写入文档版本ID的既有向量检索结果构造方式。
     */
    public VectorIndexSearchResult(String chunkId, Long documentId, String parentChunkId, Integer chunkOrder,
                                   Long sectionId, String text, String metadataJson, double score) {
        this(chunkId, documentId, null, parentChunkId, chunkOrder, sectionId, text, metadataJson, score);
    }
}
