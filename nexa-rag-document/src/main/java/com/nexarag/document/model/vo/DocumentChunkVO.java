package com.nexarag.document.model.vo;

import com.nexarag.document.enums.ChunkStatus;

import java.util.List;

/**
 * 文档片段响应。
 *
 * @param chunkId    片段ID
 * @param documentId 文档ID
 * @param chunkOrder 片段顺序
 * @param text       片段文本
 * @param status             片段状态
 * @param parentChunkId      完整父上下文片段ID
 * @param parentContext      是否为完整父上下文片段
 * @param displayOnlyHeading 是否为仅展示标题片段
 * @param childIndex         父上下文内的子片段序号
 * @param headingPath        当前片段的标题层级路径
 */
public record DocumentChunkVO(String chunkId, Long documentId, Integer chunkOrder, String text, ChunkStatus status,
                              String parentChunkId, boolean parentContext, boolean displayOnlyHeading,
                              Integer childIndex, List<String> headingPath) {
}
