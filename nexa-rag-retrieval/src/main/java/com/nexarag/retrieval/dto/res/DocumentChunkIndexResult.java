package com.nexarag.retrieval.dto.res;

/**
 * 单个文档片段索引结果，用于描述片段是否写入索引以及对应的索引ID。
 *
 * @param chunkId        片段ID
 * @param sectionId      所属章节ID
 * @param indexContent   用于索引的片段内容
 * @param success        是否成功
 * @param skipped        是否跳过索引
 * @param vectorId       向量索引ID
 * @param keywordIndexId 关键词索引ID
 * @param failureReason  失败原因
 */
public record DocumentChunkIndexResult(String chunkId,
                                       Long sectionId,
                                       String indexContent,
                                       boolean success,
                                       boolean skipped,
                                       String vectorId,
                                       String keywordIndexId,
                                       String failureReason) {

    /**
     * 兼容既有索引结果构造方式。
     */
    public DocumentChunkIndexResult(String chunkId,
                                    boolean success,
                                    boolean skipped,
                                    String vectorId,
                                    String keywordIndexId,
                                    String failureReason) {
        this(chunkId, null, null, success, skipped, vectorId, keywordIndexId, failureReason);
    }
}
