package com.nexarag.document.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.infra.messaging.document.task.DocumentStorageCleanupMessage;
import com.nexarag.infra.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 文档对象存储清理消费者，按删除任务中固化的对象名幂等清理原始与解析文件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "${nexa.document.task.storage-cleanup-topic:nexa-document-storage-cleanup}",
        consumerGroup = "${nexa.document.task.storage-cleanup-consumer-group:nexa-document-storage-cleanup-worker}",
        maxReconsumeTimes = 5)
public class RocketMqDocumentStorageCleanupConsumer implements RocketMQListener<MessageExt> {

    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private final DocumentPipelineOutboxService outboxService;

    @Override
    public void onMessage(MessageExt messageExt) {
        DocumentStorageCleanupMessage message = deserialize(messageExt);
        validateMessage(message);
        int consumeRetryCount = Math.max(messageExt.getReconsumeTimes() + 1, 1);

        // 1. 领取任务，已进入终态的重复消息直接确认
        if (!outboxService.markTaskProcessing(message.outboxId(), consumeRetryCount)) {
            return;
        }

        // 2. 逐个删除去重后的对象；任一失败交由RocketMQ重试
        for (String objectName : resolveObjectNames(message)) {
            fileStorageService.delete(objectName);
        }

        // 3. 两类对象均清理成功后更新任务状态
        outboxService.markTaskSucceeded(message.outboxId());
        log.info("文档对象存储清理完成，outboxId={}，documentId={}，operationId={}",
                message.outboxId(), message.documentId(), message.operationId());
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

    private Set<String> resolveObjectNames(DocumentStorageCleanupMessage message) {
        Set<String> objectNames = new LinkedHashSet<>();
        if (StringUtils.hasText(message.originalObjectName())) {
            objectNames.add(message.originalObjectName());
        }
        if (StringUtils.hasText(message.parsedObjectName())) {
            objectNames.add(message.parsedObjectName());
        }
        return objectNames;
    }
}
