package com.nexarag.document.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentVersionCleanupService;
import com.nexarag.document.constants.DocumentMessagingConstants;
import com.nexarag.infra.messaging.document.task.DocumentStorageCleanupMessage;
import com.nexarag.infra.messaging.document.task.DocumentVersionStorageCleanupMessage;
import com.nexarag.infra.storage.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 文档对象存储清理消费者，按消息版本幂等清理原始对象与解析制品。
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = DocumentMessagingConstants.STORAGE_CLEANUP_TOPIC,
        consumerGroup = DocumentMessagingConstants.STORAGE_CLEANUP_CONSUMER_GROUP,
        maxReconsumeTimes = DocumentMessagingConstants.MAX_RECONSUME_TIMES)
public class RocketMqDocumentStorageCleanupConsumer implements RocketMQListener<MessageExt> {

    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private final DocumentPipelineOutboxService outboxService;
    private final DocumentVersionCleanupService documentVersionCleanupService;

    @Autowired
    public RocketMqDocumentStorageCleanupConsumer(ObjectMapper objectMapper, FileStorageService fileStorageService,
                                                  DocumentPipelineOutboxService outboxService,
                                                  DocumentVersionCleanupService documentVersionCleanupService) {
        this.objectMapper = objectMapper;
        this.fileStorageService = fileStorageService;
        this.outboxService = outboxService;
        this.documentVersionCleanupService = documentVersionCleanupService;
    }

    /**
     * 兼容既有单元测试和全量文档清理消息。
     */
    public RocketMqDocumentStorageCleanupConsumer(ObjectMapper objectMapper, FileStorageService fileStorageService,
                                                  DocumentPipelineOutboxService outboxService) {
        this(objectMapper, fileStorageService, outboxService, null);
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        if (isVersionCleanupMessage(messageExt)) {
            cleanupVersion(messageExt);
            return;
        }
        DocumentStorageCleanupMessage message = deserialize(messageExt);
        validateMessage(message);
        int consumeRetryCount = Math.max(messageExt.getReconsumeTimes() + 1, 1);

        // 1. 领取任务，已进入终态的重复消息直接确认
        if (!outboxService.markTaskProcessing(message.outboxId(), consumeRetryCount)) {
            return;
        }

        // 2. 逐个删除对象和受校验前缀；任一失败交由RocketMQ重试
        CleanupTargets cleanupTargets = resolveCleanupTargets(message);
        for (String objectName : cleanupTargets.objectNames()) {
            fileStorageService.delete(objectName);
        }
        for (String objectPrefix : cleanupTargets.objectPrefixes()) {
            fileStorageService.deleteByPrefix(objectPrefix);
        }

        // 3. 两类对象均清理成功后更新任务状态
        outboxService.markTaskSucceeded(message.outboxId());
        log.info("文档对象存储清理完成，outboxId={}，documentId={}，operationId={}",
                message.outboxId(), message.documentId(), message.operationId());
    }

    private void cleanupVersion(MessageExt messageExt) {
        DocumentVersionStorageCleanupMessage message = deserializeVersion(messageExt);
        if (message.outboxId() == null || message.documentId() == null || message.documentVersionId() == null) {
            throw new ServiceException("文档版本对象存储清理消息不完整");
        }
        int consumeRetryCount = Math.max(messageExt.getReconsumeTimes() + 1, 1);
        if (!outboxService.markTaskProcessing(message.outboxId(), consumeRetryCount)) {
            return;
        }
        Set<String> objectNames = new LinkedHashSet<>();
        if (StringUtils.hasText(message.originalObjectName())) {
            objectNames.add(message.originalObjectName());
        }
        if (StringUtils.hasText(message.parsedObjectName())) {
            objectNames.add(message.parsedObjectName());
        }
        for (String objectName : objectNames) {
            fileStorageService.delete(objectName);
        }
        if (documentVersionCleanupService == null) {
            throw new ServiceException("文档版本数据清理服务不可用");
        }
        documentVersionCleanupService.cleanup(message.documentId(), message.documentVersionId());
        outboxService.markTaskSucceeded(message.outboxId());
        log.info("文档版本对象与数据清理完成，outboxId={}，documentId={}，documentVersionId={}，operationId={}",
                message.outboxId(), message.documentId(), message.documentVersionId(), message.operationId());
    }

    private void validateMessage(DocumentStorageCleanupMessage message) {
        if (message == null || message.outboxId() == null || message.documentId() == null) {
            throw new ServiceException("文档对象存储清理消息不完整");
        }
    }

    private DocumentStorageCleanupMessage deserialize(MessageExt messageExt) {
        try {
            return objectMapper.readValue(messageExt.getBody(), DocumentStorageCleanupMessage.class);
        } catch (Exception exception) {
            throw new ServiceException("解析文档对象存储清理消息失败，messageId=" + messageExt.getMsgId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private DocumentVersionStorageCleanupMessage deserializeVersion(MessageExt messageExt) {
        try {
            return objectMapper.readValue(messageExt.getBody(), DocumentVersionStorageCleanupMessage.class);
        } catch (Exception exception) {
            throw new ServiceException("解析文档版本对象存储清理消息失败，messageId=" + messageExt.getMsgId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private boolean isVersionCleanupMessage(MessageExt messageExt) {
        try {
            JsonNode messageNode = objectMapper.readTree(messageExt.getBody());
            return messageNode != null && messageNode.hasNonNull("documentVersionId");
        } catch (Exception exception) {
            throw new ServiceException("解析文档对象存储清理消息失败，messageId=" + messageExt.getMsgId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private CleanupTargets resolveCleanupTargets(DocumentStorageCleanupMessage message) {
        Set<String> objectNames = new LinkedHashSet<>();
        if (StringUtils.hasText(message.originalObjectName())) {
            objectNames.add(message.originalObjectName());
        }
        if (message.schemaVersion() == null || message.schemaVersion() < 2) {
            if (StringUtils.hasText(message.parsedObjectName())) {
                objectNames.add(message.parsedObjectName());
            }
            return new CleanupTargets(objectNames, Set.of());
        }

        String parsedPrefix = "parsed/" + message.documentId() + "/";
        String sourceSnapshotPrefix = "source-snapshots/" + message.documentId() + "/";
        if (!parsedPrefix.equals(message.parsedObjectPrefix())
                || !sourceSnapshotPrefix.equals(message.sourceSnapshotPrefix())) {
            throw new ServiceException("文档对象存储清理前缀非法，documentId=" + message.documentId());
        }
        Set<String> objectPrefixes = new LinkedHashSet<>();
        objectPrefixes.add(parsedPrefix);
        objectPrefixes.add(sourceSnapshotPrefix);
        return new CleanupTargets(objectNames, objectPrefixes);
    }

    private record CleanupTargets(Set<String> objectNames, Set<String> objectPrefixes) {
    }
}
