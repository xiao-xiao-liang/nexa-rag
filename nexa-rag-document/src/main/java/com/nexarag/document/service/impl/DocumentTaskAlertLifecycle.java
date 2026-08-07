package com.nexarag.document.service.impl;

import com.nexarag.document.model.entity.DocumentTaskOutboxDO;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.infra.alert.AlertDeliveryLifecycle;
import com.nexarag.infra.alert.model.AlertMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 文档告警任务状态回调，将 Infra 的投递结果写回文档Outbox表。
 */
@Service
@RequiredArgsConstructor
public class DocumentTaskAlertLifecycle implements AlertDeliveryLifecycle {

    private final DocumentPipelineOutboxService outboxService;

    @Override
    public boolean markProcessing(AlertMessage message, int consumeRetryCount) {
        if (!isAlertTask(message)) {
            return false;
        }
        return outboxService.markTaskProcessing(message.outboxId(), consumeRetryCount);
    }

    @Override
    public void markSucceeded(AlertMessage message) {
        if (!isAlertTask(message)) {
            return;
        }
        outboxService.markTaskSucceeded(message.outboxId());
    }

    @Override
    public void markFailed(AlertMessage message, int consumeRetryCount, String failureReason) {
        if (!isAlertTask(message)) {
            return;
        }
        outboxService.markTaskFailed(message.outboxId(), consumeRetryCount, failureReason);
    }

    private boolean isAlertTask(AlertMessage message) {
        if (message == null || message.outboxId() == null) {
            return false;
        }
        DocumentTaskOutboxDO task = outboxService.getById(message.outboxId());
        return task != null && task.getTaskType() != null && task.getTaskType().isAlertTask();
    }
}
