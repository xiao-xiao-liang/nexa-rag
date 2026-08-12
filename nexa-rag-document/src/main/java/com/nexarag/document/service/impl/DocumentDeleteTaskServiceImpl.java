package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.model.entity.DocumentTaskOutboxDO;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.enums.DocumentTaskStatus;
import com.nexarag.document.enums.DocumentTaskType;
import com.nexarag.document.enums.OutboxPublishStatus;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentDeleteTaskService;
import com.nexarag.infra.config.DocumentTaskMessagingProperties;
import com.nexarag.infra.messaging.document.task.DocumentTaskMessage;
import com.nexarag.infra.messaging.document.task.DocumentStorageCleanupMessage;
import com.nexarag.infra.storage.ObjectNameResolver;
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
    private static final int STORAGE_CLEANUP_MESSAGE_SCHEMA_VERSION = 2;

    private final DocumentPipelineOutboxService outboxService;
    private final DocumentTaskMessagingProperties taskProperties;
    private final ObjectMapper objectMapper;
    private final ObjectNameResolver objectNameResolver;

    /**
     * 创建一条可可靠发布的索引清理任务。
     */
    @Override
    public Long createIndexCleanupTask(Long documentId) {
        // 1. 生成独立执行版本和任务ID
        Long outboxId = IdWorker.getId();
        String operationId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        String messageKey = documentId + ":" + DocumentTaskType.CLEAN_DOCUMENT_INDEX + ":" + operationId;
        DocumentTaskMessage message = new DocumentTaskMessage(outboxId, documentId, null, operationId,
                DocumentTaskType.CLEAN_DOCUMENT_INDEX.name(), MESSAGE_SCHEMA_VERSION, now);

        // 2. 在当前事务中保存待发布任务
        boolean saved = outboxService.save(DocumentTaskOutboxDO.builder()
                .outboxId(outboxId)
                .documentId(documentId)
                .processId(operationId)
                .taskType(DocumentTaskType.CLEAN_DOCUMENT_INDEX)
                .messageKey(messageKey)
                .topic(taskProperties.getCleanupTopic())
                .messageBody(serialize(message))
                .publishStatus(OutboxPublishStatus.PENDING)
                .taskStatus(DocumentTaskStatus.PENDING)
                .publishRetryCount(0)
                .consumeRetryCount(0)
                .nextRetryTime(now)
                .build());
        if (!saved) {
            throw new ServiceException("保存文档索引清理任务失败，documentId=" + documentId);
        }
        return outboxId;
    }

    /**
     * 创建一条可可靠发布的对象存储清理任务。
     *
     * @param document 待删除文档
     */
    @Override
    public void createStorageCleanupTask(Document document) {
        if (document == null || document.getDocumentId() == null) {
            throw new ServiceException("创建对象存储清理任务时文档不能为空");
        }

        // 1. 生成独立执行版本和任务ID，并固化逻辑删除前的对象名
        Long outboxId = IdWorker.getId();
        String operationId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        String messageKey = document.getDocumentId() + ":" + DocumentTaskType.CLEAN_DOCUMENT_STORAGE + ":" + operationId;
        DocumentStorageCleanupMessage message = new DocumentStorageCleanupMessage(outboxId, document.getDocumentId(),
                operationId, DocumentTaskType.CLEAN_DOCUMENT_STORAGE.name(), STORAGE_CLEANUP_MESSAGE_SCHEMA_VERSION,
                document.getOriginalObjectName(), document.getParsedObjectName(),
                objectNameResolver.resolveParsedPrefix(document.getDocumentId()),
                objectNameResolver.resolveSourceSnapshotPrefix(document.getDocumentId()),
                now);

        // 2. 在当前事务中保存待发布任务，失败时与文档删除整体回滚
        boolean saved = outboxService.save(DocumentTaskOutboxDO.builder()
                .outboxId(outboxId)
                .documentId(document.getDocumentId())
                .processId(operationId)
                .taskType(DocumentTaskType.CLEAN_DOCUMENT_STORAGE)
                .messageKey(messageKey)
                .topic(taskProperties.getStorageCleanupTopic())
                .messageBody(serialize(message))
                .publishStatus(OutboxPublishStatus.PENDING)
                .taskStatus(DocumentTaskStatus.PENDING)
                .publishRetryCount(0)
                .consumeRetryCount(0)
                .nextRetryTime(now)
                .build());
        if (!saved) {
            throw new ServiceException("保存文档对象存储清理任务失败，documentId=" + document.getDocumentId());
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
