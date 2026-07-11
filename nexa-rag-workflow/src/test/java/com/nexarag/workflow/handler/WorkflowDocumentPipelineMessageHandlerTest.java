package com.nexarag.workflow.handler;

import com.nexarag.document.entity.Document;
import com.nexarag.document.service.DocumentService;
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
        DocumentService documentService = mock(DocumentService.class);
        WorkflowGraphRunner graphRunner = mock(WorkflowGraphRunner.class);
        when(documentService.getRequiredDocument(1L)).thenReturn(
                Document.builder().documentId(1L).processId("process-1").build());
        WorkflowDocumentPipelineMessageHandler handler =
                new WorkflowDocumentPipelineMessageHandler(documentService, graphRunner);

        handler.handle(message());

        verify(graphRunner).run(java.util.Map.of("documentId", 1L));
    }

    @Test
    void shouldIgnoreOldProcess() {
        DocumentService documentService = mock(DocumentService.class);
        WorkflowGraphRunner graphRunner = mock(WorkflowGraphRunner.class);
        when(documentService.getRequiredDocument(1L)).thenReturn(
                Document.builder().documentId(1L).processId("process-2").build());
        WorkflowDocumentPipelineMessageHandler handler =
                new WorkflowDocumentPipelineMessageHandler(documentService, graphRunner);

        handler.handle(message());

        verify(graphRunner, never()).run(anyMap());
    }

    private DocumentPipelineMessage message() {
        return new DocumentPipelineMessage(1L, "process-1", 1, LocalDateTime.now());
    }
}
