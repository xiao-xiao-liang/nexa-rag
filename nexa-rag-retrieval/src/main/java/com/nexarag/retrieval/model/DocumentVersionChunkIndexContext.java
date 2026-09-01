package com.nexarag.retrieval.model;

import java.util.List;

/**
 * 一次读取指定文档版本片段后得到的索引处理上下文。
 *
 * @param indexableChunks 待写入外部索引的片段
 * @param skippedChunkCount 跳过外部索引的片段数
 */
public record DocumentVersionChunkIndexContext(List<IndexableChunk> indexableChunks, int skippedChunkCount) {
}
