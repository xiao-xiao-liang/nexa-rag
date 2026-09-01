package com.nexarag.retrieval.repository;

import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.enums.ChunkStatus;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.retrieval.model.IndexableChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档片段索引仓储测试。
 */
class ChunkIndexRepositoryImplTest {

    @Test
    void listIndexableChunksForVersionShouldOnlyReadTheTargetVersionAndKeepVersionId() {
        DocumentChunkService documentChunkService = mock(DocumentChunkService.class);
        DocumentChunk chunk = DocumentChunk.builder()
                .chunkId("chunk_v2")
                .documentId(1L)
                .documentVersionId(2L)
                .text("第二版正文")
                .status(ChunkStatus.PENDING_INDEX)
                .skipIndex(0)
                .build();
        when(documentChunkService.listByDocumentVersionId(2L)).thenReturn(List.of(chunk));

        ChunkIndexRepositoryImpl repository = new ChunkIndexRepositoryImpl(documentChunkService);
        IndexableChunk result = repository.listIndexableChunks(1L, 2L).getFirst();
        repository.markSkipped(1L, 2L);

        assertThat(result.documentVersionId()).isEqualTo(2L);
        verify(documentChunkService).listByDocumentVersionId(2L);
        verify(documentChunkService).markDocumentVersionSkippedChunks(2L);
    }
}
