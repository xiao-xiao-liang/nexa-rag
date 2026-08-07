package com.nexarag.retrieval.model;

/**
 * 向量索引文档，用于承载片段文本、元数据和向量。
 *
 * @param chunkId       片段ID
 * @param documentId    文档ID
 * @param parentChunkId 父片段ID
 * @param chunkOrder    片段顺序
 * @param sectionId     所属章节ID
 * @param text          原始片段文本，仅用于回答证据展示
 * @param indexContent  用于生成向量的片段内容
 * @param metadataJson  元数据JSON
 * @param vector        向量数据
 */
public record VectorIndexDocument(String chunkId,
                                  Long documentId,
                                  String parentChunkId,
                                  Integer chunkOrder,
                                  Long sectionId,
                                  String text,
                                  String indexContent,
                                  String metadataJson,
                                  float[] vector) {

    /**
     * 兼容既有片段索引文档构造方式。
     */
    public VectorIndexDocument(String chunkId, Long documentId, String parentChunkId, Integer chunkOrder,
                               String text, String metadataJson, float[] vector) {
        this(chunkId, documentId, parentChunkId, chunkOrder, null, text, text, metadataJson, vector);
    }
}
