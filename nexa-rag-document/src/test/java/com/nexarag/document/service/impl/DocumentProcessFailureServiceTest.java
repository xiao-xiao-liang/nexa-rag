package com.nexarag.document.service.impl;

import com.nexarag.document.alert.DocumentPipelineAlertService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.infra.messaging.document.model.DocumentPipelineFailureMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档最终失败事务服务测试，验证旧轮次不会覆盖新状态或触发告警。
 */
class DocumentProcessFailureServiceTest {

    @Test
    void shouldAlertAfterCurrentProcessMarkedFailed() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentPipelineAlertService alertService = mock(DocumentPipelineAlertService.class);
        DocumentProcessFailureService service = new DocumentProcessFailureService(documentService, alertService);
        DocumentPipelineFailureMessage message = failureMessage();
        when(documentService.markProcessFailed(1L, "process-1", "INDEXING", "索引失败", "detail"))
                .thenReturn(true);

        assertThat(service.markFinalFailure(message)).isTrue();

        verify(alertService).alert(any());
    }

    @Test
    void shouldIgnoreOldProcessWithoutAlerting() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentPipelineAlertService alertService = mock(DocumentPipelineAlertService.class);
        DocumentProcessFailureService service = new DocumentProcessFailureService(documentService, alertService);

        assertThat(service.markFinalFailure(failureMessage())).isFalse();

        verify(alertService, never()).alert(any());
    }

    private DocumentPipelineFailureMessage failureMessage() {
        return new DocumentPipelineFailureMessage(
                1L, "process-1", "INDEXING", "索引失败", "detail", 6, "message-1", LocalDateTime.now());
    }
}
