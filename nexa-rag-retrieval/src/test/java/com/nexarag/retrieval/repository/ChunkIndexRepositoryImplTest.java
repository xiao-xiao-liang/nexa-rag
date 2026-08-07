package com.nexarag.retrieval.repository;

import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.enums.ChunkStatus;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.retrieval.model.IndexableChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 文档片段索引仓储测试。
 */
class ChunkIndexRepositoryImplTest {

    @Test
    void listIndexableChunksShouldKeepRawTextAndExposeIndexContent() {
        DocumentChunkService documentChunkService = mock(DocumentChunkService.class);
        DocumentChunk chunk = DocumentChunk.builder()
                .chunkId("chunk_1")
                .documentId(1L)
                .sectionId(11L)
                .text("原始正文")
                .indexContent("标题路径 > 原始正文")
                .status(ChunkStatus.PENDING_INDEX)
                .skipIndex(0)
                .build();
        when(documentChunkService.listByDocumentId(1L)).thenReturn(List.of(chunk));

        IndexableChunk result = new ChunkIndexRepositoryImpl(documentChunkService).listIndexableChunks(1L).getFirst();

        assertThat(result.text()).isEqualTo("原始正文");
        assertThat(result.sectionId()).isEqualTo(11L);
        assertThat(result.indexContent()).isEqualTo("标题路径 > 原始正文");
    }

    @Test
    void listIndexableChunksShouldFallBackToRawTextForLegacyNullIndexContent() {
        DocumentChunkService documentChunkService = mock(DocumentChunkService.class);
        DocumentChunk chunk = DocumentChunk.builder()
                .chunkId("chunk_legacy")
                .documentId(1L)
                .text("历史正文")
                .indexContent(null)
                .status(ChunkStatus.PENDING_INDEX)
                .skipIndex(0)
                .build();
        when(documentChunkService.listByDocumentId(1L)).thenReturn(List.of(chunk));

        IndexableChunk result = new ChunkIndexRepositoryImpl(documentChunkService).listIndexableChunks(1L).getFirst();

        assertThat(result.text()).isEqualTo("历史正文");
        assertThat(result.indexContent()).isEqualTo("历史正文");
    }
}
