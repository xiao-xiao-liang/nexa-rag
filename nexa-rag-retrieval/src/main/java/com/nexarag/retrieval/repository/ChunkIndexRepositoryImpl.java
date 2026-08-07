package com.nexarag.retrieval.repository;

import com.nexarag.document.entity.DocumentChunk;
import com.nexarag.document.enums.ChunkStatus;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.retrieval.model.IndexableChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档片段索引仓储实现，通过 DocumentChunkService 完成查询与回写，避免 retrieval 越层依赖 mapper。
 */
@Component
@RequiredArgsConstructor
public class ChunkIndexRepositoryImpl implements ChunkIndexRepository {

    private final DocumentChunkService documentChunkService;

    /**
     * 查询指定文档中需要写入索引的片段。
     *
     * @param documentId 文档ID
     * @return 可索引片段列表
     */
    @Override
    public List<IndexableChunk> listIndexableChunks(Long documentId) {
        // 1. 通过 document service 获取片段后在内存中过滤初版可索引数据
        return documentChunkService.listByDocumentId(documentId).stream()
                .filter(chunk -> !Integer.valueOf(1).equals(chunk.getSkipIndex()))
                .filter(chunk -> chunk.getStatus() == ChunkStatus.PENDING_INDEX || chunk.getStatus() == ChunkStatus.FAILED)
                .map(this::toIndexableChunk)
                .toList();
    }

    /**
     * 查询指定文档中跳过索引的片段。
     *
     * @param documentId 文档ID
     * @return 跳过索引片段列表
     */
    @Override
    public List<DocumentChunk> listSkippedChunks(Long documentId) {
        // 1. 初版复用全量片段查询，后续可替换为分页或条件查询
        return documentChunkService.listByDocumentId(documentId).stream()
                .filter(chunk -> Integer.valueOf(1).equals(chunk.getSkipIndex()) || chunk.getStatus() == ChunkStatus.SKIP_INDEX)
                .toList();
    }

    /**
     * 标记指定文档中的跳过索引片段。
     *
     * @param documentId 文档ID
     */
    @Override
    public void markSkipped(Long documentId) {
        documentChunkService.markDocumentSkippedChunks(documentId);
    }

    /**
     * 标记片段索引成功。
     *
     * @param chunkId        片段ID
     * @param vectorId       向量索引ID
     * @param keywordIndexId 关键词索引ID
     */
    @Override
    public void markIndexed(String chunkId, String vectorId, String keywordIndexId) {
        documentChunkService.markChunkIndexed(chunkId, vectorId, keywordIndexId);
    }

    /**
     * 标记片段索引失败。
     *
     * @param chunkId       片段ID
     * @param failureReason 失败原因
     */
    @Override
    public void markFailed(String chunkId, String failureReason) {
        documentChunkService.markChunkIndexFailed(chunkId, failureReason);
    }

    /**
     * 查询已经写入索引的片段。
     *
     * @param documentId 文档ID
     * @return 已索引片段列表
     */
    @Override
    public List<DocumentChunk> listIndexedChunks(Long documentId) {
        // 1. 初版复用全量片段查询，避免 retrieval 直接操作 mapper
        return documentChunkService.listByDocumentId(documentId).stream()
                .filter(chunk -> chunk.getStatus() == ChunkStatus.INDEXED)
                .toList();
    }

    private IndexableChunk toIndexableChunk(DocumentChunk chunk) {
        return IndexableChunk.builder()
                .chunkId(chunk.getChunkId())
                .documentId(chunk.getDocumentId())
                .chunkOrder(chunk.getChunkOrder())
                .parentChunkId(chunk.getParentChunkId())
                .text(chunk.getText())
                .metadataJson(chunk.getMetadataJson())
                .tokenCount(chunk.getTokenCount())
                .build();
    }
}