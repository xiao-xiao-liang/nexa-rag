package com.nexarag.workflow.handler;

import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import com.nexarag.workflow.service.WorkflowGraphRunner;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作流文档消息处理器测试，验证处理轮次隔离和Graph调用行为。
 */
class WorkflowDocumentPipelineMessageHandlerTest {

    @Test
    void shouldRunGraphForCurrentProcess() {
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        WorkflowGraphRunner graphRunner = mock(WorkflowGraphRunner.class);
        when(documentVersionService.getRequiredVersion(1L, 2L)).thenReturn(
                DocumentVersionDO.builder().documentId(1L).documentVersionId(2L).processId("process-1").build());
        WorkflowDocumentPipelineMessageHandler handler =
                new WorkflowDocumentPipelineMessageHandler(documentVersionService, graphRunner);

        handler.handle(message());

        verify(graphRunner).run(java.util.Map.of("documentId", 1L, "documentVersionId", 2L, "processId", "process-1"));
    }

    @Test
    void shouldIgnoreOldProcess() {
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        WorkflowGraphRunner graphRunner = mock(WorkflowGraphRunner.class);
        when(documentVersionService.getRequiredVersion(1L, 2L)).thenReturn(
                DocumentVersionDO.builder().documentId(1L).documentVersionId(2L).processId("process-2").build());
        WorkflowDocumentPipelineMessageHandler handler =
                new WorkflowDocumentPipelineMessageHandler(documentVersionService, graphRunner);

        handler.handle(message());

        verify(graphRunner, never()).run(anyMap());
    }

    @Test
    void shouldRejectMissingMessageBoundary() {
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        WorkflowGraphRunner graphRunner = mock(WorkflowGraphRunner.class);
        WorkflowDocumentPipelineMessageHandler handler =
                new WorkflowDocumentPipelineMessageHandler(documentVersionService, graphRunner);

        handler.handle(null);

        verify(documentVersionService, never()).getRequiredVersion(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(graphRunner, never()).run(anyMap());
    }

    private DocumentPipelineMessage message() {
        return new DocumentPipelineMessage(1L, 2L, "process-1", 101L, 2, LocalDateTime.now());
    }
}
