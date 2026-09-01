package com.nexarag.workflow.node.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.service.DocumentChunkingService;
import com.nexarag.document.service.DocumentVersionService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.INDEXING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_ID;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_VERSION_ID;
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
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        DocumentChunkingService chunkingService = mock(DocumentChunkingService.class);
        when(chunkingService.chunk(1001L, 2001L)).thenReturn(8);
        when(documentVersionService.getRequiredVersion(1001L, 2001L)).thenReturn(DocumentVersionDO.builder()
                .documentId(1001L)
                .documentVersionId(2001L)
                .status(DocumentVersionStatus.CHUNKED)
                .build());

        ChunkingNode node = new ChunkingNode(documentVersionService, chunkingService);
        Map<String, Object> result = node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L, DOCUMENT_VERSION_ID, 2001L)));

        assertThat(result).containsEntry(ROUTE_TARGET, INDEXING_NODE);
    }

    @Test
    void applyShouldEndWhenChunkingFailureExhausted() throws Exception {
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        DocumentChunkingService chunkingService = mock(DocumentChunkingService.class);
        when(chunkingService.chunk(1001L, 2001L)).thenReturn(0);
        when(documentVersionService.getRequiredVersion(1001L, 2001L)).thenReturn(DocumentVersionDO.builder()
                .documentId(1001L)
                .documentVersionId(2001L)
                .status(DocumentVersionStatus.FAILED)
                .build());

        ChunkingNode node = new ChunkingNode(documentVersionService, chunkingService);
        Map<String, Object> result = node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L, DOCUMENT_VERSION_ID, 2001L)));

        assertThat(result).containsEntry(ROUTE_TARGET, END);
    }

    @Test
    void applyShouldPropagateRetryException() {
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        DocumentChunkingService chunkingService = mock(DocumentChunkingService.class);
        when(chunkingService.chunk(1001L, 2001L)).thenThrow(new IllegalStateException("切分失败"));

        ChunkingNode node = new ChunkingNode(documentVersionService, chunkingService);

        assertThatThrownBy(() -> node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L, DOCUMENT_VERSION_ID, 2001L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("切分失败");
    }
}
