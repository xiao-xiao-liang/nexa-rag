package com.nexarag.retrieval.repository;

import com.nexarag.document.enums.ChunkStatus;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.retrieval.model.IndexableChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 文档片段索引仓储实现，通过 DocumentChunkService 完成查询与回写，避免 retrieval 越层依赖 mapper。
 */
@Component
@RequiredArgsConstructor
public class ChunkIndexRepositoryImpl implements ChunkIndexRepository {

    private final DocumentChunkService documentChunkService;

    /**
     * 查询指定文档版本中需要写入索引的片段。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @return 可索引片段列表
     */
    @Override
    public List<IndexableChunk> listIndexableChunks(Long documentId, Long documentVersionId) {
        // 1. 按版本读取并二次校验文档归属，防止跨文档版本数据进入索引
        return documentChunkService.listByDocumentVersionId(documentVersionId).stream()
                .filter(chunk -> documentId.equals(chunk.getDocumentId()))
                .filter(chunk -> !Integer.valueOf(1).equals(chunk.getSkipIndex()))
                .filter(chunk -> chunk.getStatus() == ChunkStatus.PENDING_INDEX || chunk.getStatus() == ChunkStatus.FAILED)
                .map(this::toIndexableChunk)
                .toList();
    }

    @Override
    public List<DocumentChunk> listSkippedChunks(Long documentId, Long documentVersionId) {
        return documentChunkService.listByDocumentVersionId(documentVersionId).stream()
                .filter(chunk -> documentId.equals(chunk.getDocumentId()))
                .filter(chunk -> Integer.valueOf(1).equals(chunk.getSkipIndex())
                        || chunk.getStatus() == ChunkStatus.SKIP_INDEX)
                .toList();
    }

    /**
     * 标记指定文档版本中的跳过索引片段。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     */
    @Override
    public void markSkipped(Long documentId, Long documentVersionId) {
        if (documentId == null || documentVersionId == null) {
            throw new IllegalArgumentException("文档ID和文档版本ID不能为空");
        }
        documentChunkService.markDocumentVersionSkippedChunks(documentVersionId);
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

    private IndexableChunk toIndexableChunk(DocumentChunk chunk) {
        return IndexableChunk.builder()
                .chunkId(chunk.getChunkId())
                .documentId(chunk.getDocumentId())
                .documentVersionId(chunk.getDocumentVersionId())
                .chunkOrder(chunk.getChunkOrder())
                .parentChunkId(chunk.getParentChunkId())
                .sectionId(chunk.getSectionId())
                .text(chunk.getText())
                .indexContent(StringUtils.hasText(chunk.getIndexContent()) ? chunk.getIndexContent() : chunk.getText())
                .metadataJson(chunk.getMetadataJson())
                .tokenCount(chunk.getTokenCount())
                .build();
    }
}
