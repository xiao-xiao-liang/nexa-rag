package com.nexarag.document.splitter;

import java.util.Map;

/**
 * 待保存的片段草稿。
 *
 * @param text      片段文本
 * @param metadata  片段元数据
 * @param skipIndex 是否跳过索引
 */
public record ChunkDraft(String text, Map<String, Object> metadata, boolean skipIndex) {
}
