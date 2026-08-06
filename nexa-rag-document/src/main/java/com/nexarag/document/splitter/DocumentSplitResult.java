package com.nexarag.document.splitter;

import java.util.List;

/**
 * 文档切分结果，统一承载章节草稿与正文片段草稿。
 *
 * @param sections   章节草稿
 * @param chunks     片段草稿
 * @param structured 是否包含结构化章节信息
 */
public record DocumentSplitResult(List<DocumentSectionDraft> sections,
                                  List<ChunkDraft> chunks,
                                  boolean structured) {

    public DocumentSplitResult {
        sections = sections == null ? List.of() : List.copyOf(sections);
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    /**
     * 创建不含章节结构的切分结果。
     *
     * @param chunks 片段草稿
     * @return 非结构化切分结果
     */
    public static DocumentSplitResult unstructured(List<ChunkDraft> chunks) {
        List<ChunkDraft> unstructuredChunks = chunks == null ? List.of() : chunks.stream()
                .map(chunk -> new ChunkDraft(chunk.chunkId(), chunk.parentChunkId(), null,
                        chunk.text(), chunk.text(), chunk.tokenCount(), chunk.metadata(), chunk.skipIndex()))
                .toList();
        return new DocumentSplitResult(List.of(), unstructuredChunks, false);
    }
}
