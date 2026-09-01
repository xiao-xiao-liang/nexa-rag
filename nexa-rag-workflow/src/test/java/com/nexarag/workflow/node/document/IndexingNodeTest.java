package com.nexarag.workflow.node.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.retrieval.dto.res.DocumentIndexResult;
import com.nexarag.retrieval.service.DocumentIndexService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_ID;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_VERSION_ID;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.PROCESS_ID;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.ROUTE_TARGET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 索引节点测试，验证索引成功、可重试失败和失败耗尽行为。
 */
class IndexingNodeTest {

    @Test
    void applyShouldIndexDocumentAndEnd() throws Exception {
        DocumentService documentService = mock(DocumentService.class);
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        DocumentIndexService indexService = mock(DocumentIndexService.class);
        when(indexService.indexDocument(1001L, 2001L)).thenReturn(successResult());

        IndexingNode node = new IndexingNode(documentService, documentVersionService, indexService);
        Map<String, Object> result = node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L, DOCUMENT_VERSION_ID, 2001L,
                PROCESS_ID, "process-1")));

        assertThat(result).containsEntry(ROUTE_TARGET, END);
    }

    @Test
    void applyShouldThrowWhenIndexFailureNeedsRetry() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        DocumentIndexService indexService = mock(DocumentIndexService.class);
        when(indexService.indexDocument(1001L, 2001L)).thenReturn(failureResult());
        when(documentVersionService.getRequiredVersion(1001L, 2001L)).thenReturn(DocumentVersionDO.builder()
                .documentId(1001L).documentVersionId(2001L).processId("process-1").status(DocumentVersionStatus.QUEUED)
                .build());

        IndexingNode node = new IndexingNode(documentService, documentVersionService, indexService);

        assertThatThrownBy(() -> node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L, DOCUMENT_VERSION_ID, 2001L,
                PROCESS_ID, "process-1"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文档版本索引失败");
    }

    @Test
    void applyShouldEndWhenIndexFailureExhausted() throws Exception {
        DocumentService documentService = mock(DocumentService.class);
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        DocumentIndexService indexService = mock(DocumentIndexService.class);
        when(indexService.indexDocument(1001L, 2001L)).thenReturn(failureResult());
        when(documentVersionService.getRequiredVersion(1001L, 2001L)).thenReturn(DocumentVersionDO.builder()
                .documentId(1001L).documentVersionId(2001L).processId("process-1").status(DocumentVersionStatus.FAILED)
                .build());

        IndexingNode node = new IndexingNode(documentService, documentVersionService, indexService);
        Map<String, Object> result = node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L, DOCUMENT_VERSION_ID, 2001L,
                PROCESS_ID, "process-1")));

        assertThat(result).containsEntry(ROUTE_TARGET, END);
    }

    private DocumentIndexResult successResult() {
        return new DocumentIndexResult(1001L, true, 8, 8, 0, 0,
                true, true, null, List.of());
    }

    private DocumentIndexResult failureResult() {
        return new DocumentIndexResult(1001L, false, 8, 4, 0, 4,
                true, true, "索引失败", List.of());
    }
}
