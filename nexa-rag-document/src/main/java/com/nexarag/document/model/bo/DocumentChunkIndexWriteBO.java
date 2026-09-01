package com.nexarag.document.model.bo;

/**
 * 文档片段索引成功后的状态回写数据。
 *
 * @param chunkId 片段ID
 * @param vectorId 向量索引ID
 * @param keywordIndexId 关键词索引ID
 */
public record DocumentChunkIndexWriteBO(String chunkId, String vectorId, String keywordIndexId) {
}
