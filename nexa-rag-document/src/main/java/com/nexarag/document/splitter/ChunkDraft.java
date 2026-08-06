package com.nexarag.document.splitter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 待保存的片段草稿。
 *
 * @param chunkId       片段ID
 * @param parentChunkId 父片段ID
 * @param sectionId     所属章节ID
 * @param text          片段文本
 * @param indexContent  用于索引的片段内容
 * @param tokenCount    Token 数量
 * @param metadata      片段元数据
 * @param skipIndex     是否跳过索引
 */
public record ChunkDraft(String chunkId,
                         String parentChunkId,
                         Long sectionId,
                         String text,
                         String indexContent,
                         Integer tokenCount,
                         Map<String, Object> metadata,
                         boolean skipIndex) {

    public ChunkDraft {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /**
     * 兼容既有不含章节和索引内容的构造方式。
     *
     * @param chunkId       片段ID
     * @param parentChunkId 父片段ID
     * @param text          片段文本
     * @param tokenCount    Token 数量
     * @param metadata      片段元数据
     * @param skipIndex     是否跳过索引
     */
    public ChunkDraft(String chunkId,
                      String parentChunkId,
                      String text,
                      Integer tokenCount,
                      Map<String, Object> metadata,
                      boolean skipIndex) {
        this(chunkId, parentChunkId, null, text, text, tokenCount, metadata, skipIndex);
    }

    /**
     * 兼容阶段二测试中只关心文本、元数据和索引标记的构造方式。
     *
     * @param text      片段文本
     * @param metadata  片段元数据
     * @param skipIndex 是否跳过索引
     */
    public ChunkDraft(String text, Map<String, Object> metadata, boolean skipIndex) {
        this(null, null, null, text, text, null, metadata, skipIndex);
    }
}
