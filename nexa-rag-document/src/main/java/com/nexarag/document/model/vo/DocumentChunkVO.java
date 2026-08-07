package com.nexarag.document.model.vo;

import com.nexarag.document.enums.ChunkStatus;

/**
 * 文档片段响应。
 *
 * @param chunkId    片段ID
 * @param documentId 文档ID
 * @param chunkOrder 片段顺序
 * @param text       片段文本
 * @param status     片段状态
 */
public record DocumentChunkVO(String chunkId, Long documentId, Integer chunkOrder, String text, ChunkStatus status) {
}
