package com.nexarag.retrieval.model;

/**
 * 关键词索引文档，用于承载片段文本和元数据。
 *
 * @param chunkId           片段ID
 * @param documentId        文档ID
 * @param documentVersionId 文档版本ID
 * @param parentChunkId     父片段ID
 * @param chunkOrder        片段顺序
 * @param sectionId         所属章节ID
 * @param text              原始片段文本，仅用于回答证据展示
 * @param indexContent      用于关键词检索的片段内容
 * @param metadataJson      元数据JSON
 */
public record KeywordIndexDocument(String chunkId,
                                   Long documentId,
                                   Long documentVersionId,
                                   String parentChunkId,
                                   Integer chunkOrder,
                                   Long sectionId,
                                   String text,
                                   String indexContent,
                                   String metadataJson) {

    /**
     * 兼容既有片段索引文档构造方式。
     */
    public KeywordIndexDocument(String chunkId, Long documentId, String parentChunkId, Integer chunkOrder,
                                String text, String metadataJson) {
        this(chunkId, documentId, null, parentChunkId, chunkOrder, null, text, text, metadataJson);
    }

    /**
     * 兼容未写入文档版本ID的既有片段索引文档构造方式。
     */
    public KeywordIndexDocument(String chunkId, Long documentId, String parentChunkId, Integer chunkOrder,
                                Long sectionId, String text, String indexContent, String metadataJson) {
        this(chunkId, documentId, null, parentChunkId, chunkOrder, sectionId, text, indexContent, metadataJson);
    }
}
