package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.DocumentTaskStatus;
import com.nexarag.document.enums.DocumentTaskType;
import com.nexarag.document.enums.OutboxPublishStatus;
import com.nexarag.document.model.entity.DocumentTaskOutboxDO;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentTaskAlertService;
import com.nexarag.infra.alert.model.AlertChannel;
import com.nexarag.infra.alert.model.AlertMessage;
import com.nexarag.infra.alert.model.AlertSeverity;
import com.nexarag.infra.config.AlertProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文档任务最终失败告警服务实现，在父任务状态事务内写入两个独立渠道任务。
 */
@Service
@RequiredArgsConstructor
public class DocumentTaskAlertServiceImpl implements DocumentTaskAlertService {

    private static final int MESSAGE_SCHEMA_VERSION = 1;

    private final DocumentPipelineOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final AlertProperties alertProperties;

    @Override
    public void createFailureAlerts(Long parentOutboxId, int consumeRetryCount, String failureReason) {
        // 1. 查询并校验父任务，告警任务禁止再次产生告警任务
        DocumentTaskOutboxDO parentTask = outboxService.getById(parentOutboxId);
        if (parentTask == null) {
            throw new ServiceException("父文档任务不存在，outboxId=" + parentOutboxId);
        }
        if (parentTask.getTaskType() == null || parentTask.getTaskType().isAlertTask()) {
            throw new ServiceException("不允许为告警任务创建失败告警，outboxId=" + parentOutboxId);
        }

        // 2. 为两个渠道分别保存待发布任务，任一失败都由调用方事务整体回滚
        LocalDateTime failureTime = LocalDateTime.now();
        saveAlertTask(parentTask, AlertChannel.FEISHU, DocumentTaskType.SEND_FEISHU_FAILURE_ALERT,
                consumeRetryCount, failureReason, failureTime);
        saveAlertTask(parentTask, AlertChannel.EMAIL, DocumentTaskType.SEND_EMAIL_FAILURE_ALERT,
                consumeRetryCount, failureReason, failureTime);
    }

    private void saveAlertTask(DocumentTaskOutboxDO parentTask, AlertChannel channel, DocumentTaskType taskType,
                               int consumeRetryCount, String failureReason, LocalDateTime failureTime) {
        Long outboxId = IdWorker.getId();
        String operationId = UUID.randomUUID().toString().replace("-", "");
        AlertMessage message = new AlertMessage(outboxId, parentTask.getDocumentId(), parentTask.getOutboxId(),
                operationId, parentTask.getTaskType().name(), resolveSeverity(parentTask.getTaskType()), channel,
                failureReason, Math.max(consumeRetryCount, 1), failureTime);
        DocumentTaskOutboxDO task = DocumentTaskOutboxDO.builder()
                .outboxId(outboxId)
                .documentId(parentTask.getDocumentId())
                .parentOutboxId(parentTask.getOutboxId())
                .processId(operationId)
                .taskType(taskType)
                .messageKey(parentTask.getOutboxId() + ":" + taskType + ":" + operationId)
                .topic(alertProperties.getTopic())
                .messageBody(serialize(message))
                .publishStatus(OutboxPublishStatus.PENDING)
                .taskStatus(DocumentTaskStatus.PENDING)
                .publishRetryCount(0)
                .consumeRetryCount(0)
                .nextRetryTime(failureTime)
                .build();
        if (!outboxService.save(task)) {
            throw new ServiceException("保存文档失败告警任务失败，parentOutboxId=" + parentTask.getOutboxId()
                    + "，channel=" + channel);
        }
    }

    private AlertSeverity resolveSeverity(DocumentTaskType parentTaskType) {
        return parentTaskType == DocumentTaskType.PROCESS_DOCUMENT ? AlertSeverity.WARNING : AlertSeverity.ERROR;
    }

    private String serialize(AlertMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("序列化文档失败告警消息失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
