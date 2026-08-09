package com.nexarag.document.service.impl;

import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentTaskAlertService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档任务最终失败处理服务测试。
 */
class DocumentTaskFinalFailureServiceImplTest {

    @Test
    void shouldCreateAlertsWhenTaskIsFirstMarkedFailed() {
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentTaskAlertService alertService = mock(DocumentTaskAlertService.class);
        when(outboxService.markTaskFailed(eq(101L), eq(6), any())).thenReturn(true);
        DocumentTaskFinalFailureServiceImpl service = new DocumentTaskFinalFailureServiceImpl(outboxService, alertService);

        service.markFailedAndCreateAlerts(101L, 6, "文档对象存储清理任务进入RocketMQ死信队列");

        verify(alertService).createFailureAlerts(101L, 6, "文档对象存储清理任务进入RocketMQ死信队列");
    }

    @Test
    void shouldNotCreateDuplicateAlertsWhenTaskWasAlreadyFailed() {
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentTaskAlertService alertService = mock(DocumentTaskAlertService.class);
        when(outboxService.markTaskFailed(eq(101L), eq(6), any())).thenReturn(false);
        DocumentTaskFinalFailureServiceImpl service = new DocumentTaskFinalFailureServiceImpl(outboxService, alertService);

        service.markFailedAndCreateAlerts(101L, 6, "文档对象存储清理任务进入RocketMQ死信队列");

        verify(alertService, never()).createFailureAlerts(101L, 6, "文档对象存储清理任务进入RocketMQ死信队列");
    }
}
