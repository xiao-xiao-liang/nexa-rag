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
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentDeleteTaskService;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.infra.config.DocumentTaskMessagingProperties;
import com.nexarag.infra.messaging.document.task.DocumentVersionIndexCleanupMessage;
import com.nexarag.infra.messaging.document.task.DocumentVersionStorageCleanupMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文档删除任务创建服务，在调用方事务内写入索引清理 Outbox 记录。
 */
@Service
@RequiredArgsConstructor
public class DocumentDeleteTaskServiceImpl implements DocumentDeleteTaskService {

    private static final int MESSAGE_SCHEMA_VERSION = 1;
    private final DocumentPipelineOutboxService outboxService;
    private final DocumentTaskMessagingProperties taskProperties;
    private final ObjectMapper objectMapper;

    @Override
    public Long createVersionIndexCleanupTask(Long documentId, Long documentVersionId) {
        if (documentId == null || documentVersionId == null) {
            throw new ServiceException("创建文档版本索引清理任务时标识不能为空");
        }
        Long outboxId = IdWorker.getId();
        String operationId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        String messageKey = documentId + ":" + documentVersionId + ":"
                + DocumentTaskType.CLEAN_DOCUMENT_VERSION_INDEX + ":" + operationId;
        DocumentVersionIndexCleanupMessage message = new DocumentVersionIndexCleanupMessage(outboxId, documentId,
                documentVersionId, operationId, DocumentTaskType.CLEAN_DOCUMENT_VERSION_INDEX.name(),
                MESSAGE_SCHEMA_VERSION, now);
        boolean saved = outboxService.save(DocumentTaskOutboxDO.builder()
                .outboxId(outboxId).documentId(documentId).documentVersionId(documentVersionId)
                .processId(operationId).taskType(DocumentTaskType.CLEAN_DOCUMENT_VERSION_INDEX)
                .messageKey(messageKey).topic(taskProperties.getCleanupTopic()).messageBody(serialize(message))
                .publishStatus(OutboxPublishStatus.PENDING).taskStatus(DocumentTaskStatus.PENDING)
                .publishRetryCount(0).consumeRetryCount(0).nextRetryTime(now).build());
        if (!saved) {
            throw new ServiceException("保存文档版本索引清理任务失败，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId);
        }
        return outboxId;
    }

    @Override
    public void createVersionStorageCleanupTask(DocumentVersionDO documentVersion) {
        if (documentVersion == null || documentVersion.getDocumentId() == null
                || documentVersion.getDocumentVersionId() == null) {
            throw new ServiceException("创建文档版本对象存储清理任务时版本不能为空");
        }
        Long outboxId = IdWorker.getId();
        String operationId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        String messageKey = documentVersion.getDocumentId() + ":" + documentVersion.getDocumentVersionId() + ":"
                + DocumentTaskType.CLEAN_DOCUMENT_VERSION_STORAGE + ":" + operationId;
        DocumentVersionStorageCleanupMessage message = new DocumentVersionStorageCleanupMessage(outboxId,
                documentVersion.getDocumentId(), documentVersion.getDocumentVersionId(), operationId,
                DocumentTaskType.CLEAN_DOCUMENT_VERSION_STORAGE.name(), MESSAGE_SCHEMA_VERSION,
                documentVersion.getOriginalObjectName(), documentVersion.getParsedObjectName(), now);
        boolean saved = outboxService.save(DocumentTaskOutboxDO.builder()
                .outboxId(outboxId).documentId(documentVersion.getDocumentId())
                .documentVersionId(documentVersion.getDocumentVersionId()).processId(operationId)
                .taskType(DocumentTaskType.CLEAN_DOCUMENT_VERSION_STORAGE).messageKey(messageKey)
                .topic(taskProperties.getStorageCleanupTopic()).messageBody(serialize(message))
                .publishStatus(OutboxPublishStatus.PENDING).taskStatus(DocumentTaskStatus.PENDING)
                .publishRetryCount(0).consumeRetryCount(0).nextRetryTime(now).build());
        if (!saved) {
            throw new ServiceException("保存文档版本对象存储清理任务失败，documentId="
                    + documentVersion.getDocumentId() + "，documentVersionId=" + documentVersion.getDocumentVersionId());
        }
    }

    private String serialize(Object message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("序列化文档索引清理消息失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
