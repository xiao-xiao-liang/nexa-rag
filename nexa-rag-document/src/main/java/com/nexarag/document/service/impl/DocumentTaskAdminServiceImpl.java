package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.DocumentTaskStatus;
import com.nexarag.document.enums.OutboxPublishStatus;
import com.nexarag.document.model.entity.DocumentTaskOutboxDO;
import com.nexarag.document.model.vo.DocumentTaskVO;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentTaskAdminService;
import com.nexarag.infra.alert.model.AlertMessage;
import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import com.nexarag.infra.messaging.document.task.DocumentStorageCleanupMessage;
import com.nexarag.infra.messaging.document.task.DocumentTaskMessage;
import com.nexarag.infra.messaging.document.task.DocumentVersionIndexCleanupMessage;
import com.nexarag.infra.messaging.document.task.DocumentVersionStorageCleanupMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文档异步任务管理服务实现，复制失败任务为新的可追踪任务。
 */
@Service
@RequiredArgsConstructor
public class DocumentTaskAdminServiceImpl implements DocumentTaskAdminService {

    private final DocumentPipelineOutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Override
    public DocumentTaskVO getTask(Long outboxId) {
        return toTaskVO(getRequiredTask(outboxId));
    }

    @Override
    public DocumentTaskVO retryFailedTask(Long outboxId) {
        DocumentTaskOutboxDO failedTask = getRequiredTask(outboxId);
        if (failedTask.getTaskStatus() != DocumentTaskStatus.FAILED) {
            throw new ClientException("只有最终失败任务允许人工重试，outboxId=" + outboxId);
        }
        String operationId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        Long retryOutboxId = IdWorker.getId();
        DocumentTaskOutboxDO retryTask = DocumentTaskOutboxDO.builder()
                .outboxId(retryOutboxId)
                .documentId(failedTask.getDocumentId())
                .documentVersionId(failedTask.getDocumentVersionId())
                .parentOutboxId(failedTask.getParentOutboxId())
                .processId(operationId)
                .taskType(failedTask.getTaskType())
                .messageKey(buildMessageKey(failedTask, operationId))
                .topic(failedTask.getTopic())
                .messageBody(rebuildMessageBody(failedTask, retryOutboxId, operationId, now))
                .publishStatus(OutboxPublishStatus.PENDING)
                .taskStatus(DocumentTaskStatus.PENDING)
                .publishRetryCount(0)
                .consumeRetryCount(0)
                .nextRetryTime(now)
                .build();
        if (!outboxService.save(retryTask)) {
            throw new ServiceException("保存文档任务重试记录失败，outboxId=" + outboxId);
        }
        return toTaskVO(retryTask);
    }

    private DocumentTaskOutboxDO getRequiredTask(Long outboxId) {
        DocumentTaskOutboxDO task = outboxService.getById(outboxId);
        if (task == null) {
            throw new ClientException("文档任务不存在，outboxId=" + outboxId);
        }
        return task;
    }

    private DocumentTaskVO toTaskVO(DocumentTaskOutboxDO task) {
        String failureReason = task.getTaskFailureReason() != null
                ? task.getTaskFailureReason()
                : task.getFailureReason();
        return new DocumentTaskVO(task.getOutboxId(), task.getDocumentId(), task.getParentOutboxId(),
                task.getProcessId(), task.getTaskType(), task.getPublishStatus(), task.getTaskStatus(),
                task.getPublishRetryCount(), task.getConsumeRetryCount(), failureReason, task.getTaskCompletedTime());
    }

    private String rebuildMessageBody(DocumentTaskOutboxDO failedTask, Long retryOutboxId, String operationId,
                                      LocalDateTime createdTime) {
        try {
            Object retryMessage = switch (failedTask.getTaskType()) {
                case PROCESS_DOCUMENT -> new DocumentPipelineMessage(failedTask.getDocumentId(),
                        requireDocumentVersionId(failedTask), operationId, retryOutboxId, 2, createdTime);
                case CLEAN_DOCUMENT_INDEX -> {
                    DocumentTaskMessage previous = objectMapper.readValue(failedTask.getMessageBody(),
                            DocumentTaskMessage.class);
                    yield new DocumentTaskMessage(retryOutboxId, failedTask.getDocumentId(),
                            failedTask.getParentOutboxId(), operationId, failedTask.getTaskType().name(),
                            previous.schemaVersion(), createdTime);
                }
                case CLEAN_DOCUMENT_STORAGE -> {
                    DocumentStorageCleanupMessage previous = objectMapper.readValue(failedTask.getMessageBody(),
                            DocumentStorageCleanupMessage.class);
                    yield new DocumentStorageCleanupMessage(retryOutboxId, failedTask.getDocumentId(), operationId,
                            failedTask.getTaskType().name(), previous.schemaVersion(), previous.originalObjectName(),
                            previous.parsedObjectName(), previous.parsedObjectPrefix(),
                            previous.sourceSnapshotPrefix(), createdTime);
                }
                case CLEAN_DOCUMENT_VERSION_INDEX -> {
                    DocumentVersionIndexCleanupMessage previous = objectMapper.readValue(failedTask.getMessageBody(),
                            DocumentVersionIndexCleanupMessage.class);
                    yield new DocumentVersionIndexCleanupMessage(retryOutboxId, failedTask.getDocumentId(),
                            failedTask.getDocumentVersionId(), operationId, failedTask.getTaskType().name(),
                            previous.schemaVersion(), createdTime);
                }
                case CLEAN_DOCUMENT_VERSION_STORAGE -> {
                    DocumentVersionStorageCleanupMessage previous = objectMapper.readValue(failedTask.getMessageBody(),
                            DocumentVersionStorageCleanupMessage.class);
                    yield new DocumentVersionStorageCleanupMessage(retryOutboxId, failedTask.getDocumentId(),
                            failedTask.getDocumentVersionId(), operationId, failedTask.getTaskType().name(),
                            previous.schemaVersion(), previous.originalObjectName(), previous.parsedObjectName(),
                            createdTime);
                }
                case SEND_FEISHU_FAILURE_ALERT, SEND_EMAIL_FAILURE_ALERT -> {
                    AlertMessage previous = objectMapper.readValue(failedTask.getMessageBody(), AlertMessage.class);
                    yield new AlertMessage(retryOutboxId, failedTask.getDocumentId(), failedTask.getParentOutboxId(),
                            operationId, previous.taskType(), previous.severity(), previous.channel(),
                            previous.failureReason(), previous.consumeRetryCount(), previous.failureTime());
                }
            };
            return objectMapper.writeValueAsString(retryMessage);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("重建文档任务重试消息失败，outboxId=" + failedTask.getOutboxId(), exception,
                    com.nexarag.common.error.BaseErrorCode.SERVICE_ERROR);
        }
    }

    private String buildMessageKey(DocumentTaskOutboxDO task, String operationId) {
        if (task.getDocumentVersionId() == null) {
            return task.getDocumentId() + ":" + task.getTaskType() + ":" + operationId;
        }
        return task.getDocumentId() + ":" + task.getDocumentVersionId() + ":" + task.getTaskType()
                + ":" + operationId;
    }

    private Long requireDocumentVersionId(DocumentTaskOutboxDO task) {
        Long documentVersionId = task.getDocumentVersionId();
        if (documentVersionId == null || documentVersionId <= 0) {
            throw new ClientException("文档处理任务缺少文档版本边界，outboxId=" + task.getOutboxId());
        }
        return documentVersionId;
    }
}
