package com.nexarag.document.service.impl;

import com.nexarag.document.service.DocumentTaskAlertService;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.infra.messaging.document.model.DocumentPipelineFailureMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档最终失败事务服务测试，验证旧轮次不会覆盖新状态或触发告警。
 */
class DocumentProcessFailureServiceTest {

    @Test
    void shouldCreateAlertTasksAfterCurrentProcessMarkedFailed() {
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        DocumentTaskAlertService alertService = mock(DocumentTaskAlertService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentProcessFailureService service = new DocumentProcessFailureService(documentVersionService, alertService, outboxService);
        DocumentPipelineFailureMessage message = failureMessage();
        when(documentVersionService.markProcessFailed(1L, 2L, "process-1", "INDEXING", "索引失败", "detail",
                6, "message-1", message.failureTime()))
                .thenReturn(true);

        assertThat(service.markFinalFailure(message)).isTrue();

        verify(outboxService).markTaskFailed(101L, 6, "索引失败");
        verify(alertService).createFailureAlerts(101L, 6, "索引失败");
    }

    @Test
    void shouldIgnoreOldProcessWithoutCreatingAlertTasks() {
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        DocumentTaskAlertService alertService = mock(DocumentTaskAlertService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentProcessFailureService service = new DocumentProcessFailureService(documentVersionService, alertService, outboxService);

        assertThat(service.markFinalFailure(failureMessage())).isFalse();

        verify(alertService, never()).createFailureAlerts(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
        verify(outboxService, never()).markTaskFailed(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    private DocumentPipelineFailureMessage failureMessage() {
        return new DocumentPipelineFailureMessage(
                101L, 1L, 2L, "process-1", "INDEXING", "索引失败", "detail", 6, "message-1", LocalDateTime.now());
    }
}
