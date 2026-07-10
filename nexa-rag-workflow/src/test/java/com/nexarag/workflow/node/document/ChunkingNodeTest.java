package com.nexarag.workflow.node.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.document.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.service.DocumentChunkingService;
import com.nexarag.document.service.DocumentService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.INDEXING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_ID;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.ROUTE_TARGET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 切分节点测试，验证切分阶段完成、失败耗尽和异常传播行为。
 */
class ChunkingNodeTest {

    @Test
    void applyShouldChunkDocumentAndRouteToIndexing() throws Exception {
        DocumentService documentService = mock(DocumentService.class);
        DocumentChunkingService chunkingService = mock(DocumentChunkingService.class);
        when(chunkingService.chunk(1001L)).thenReturn(8);
        when(documentService.getRequiredDocument(1001L)).thenReturn(Document.builder()
                .documentId(1001L)
                .status(DocumentStatus.CHUNKED)
                .build());

        ChunkingNode node = new ChunkingNode(documentService, chunkingService);
        Map<String, Object> result = node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L)));

        assertThat(result).containsEntry(ROUTE_TARGET, INDEXING_NODE);
    }

    @Test
    void applyShouldEndWhenChunkingFailureExhausted() throws Exception {
        DocumentService documentService = mock(DocumentService.class);
        DocumentChunkingService chunkingService = mock(DocumentChunkingService.class);
        when(chunkingService.chunk(1001L)).thenReturn(0);
        when(documentService.getRequiredDocument(1001L)).thenReturn(Document.builder()
                .documentId(1001L)
                .status(DocumentStatus.FAILED)
                .build());

        ChunkingNode node = new ChunkingNode(documentService, chunkingService);
        Map<String, Object> result = node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L)));

        assertThat(result).containsEntry(ROUTE_TARGET, END);
    }

    @Test
    void applyShouldPropagateRetryException() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentChunkingService chunkingService = mock(DocumentChunkingService.class);
        when(chunkingService.chunk(1001L)).thenThrow(new IllegalStateException("切分失败"));

        ChunkingNode node = new ChunkingNode(documentService, chunkingService);

        assertThatThrownBy(() -> node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("切分失败");
    }
}
