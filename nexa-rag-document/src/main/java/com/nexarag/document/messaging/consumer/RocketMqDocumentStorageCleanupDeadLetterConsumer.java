package com.nexarag.document.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.service.DocumentTaskFinalFailureService;
import com.nexarag.infra.messaging.document.task.DocumentStorageCleanupMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 对象存储清理任务死信消费者，标记最终失败并创建独立渠道告警任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "%DLQ%${nexa.document.task.storage-cleanup-consumer-group:nexa-document-storage-cleanup-worker}",
        consumerGroup = "${nexa.document.task.storage-cleanup-dead-letter-consumer-group:nexa-document-storage-cleanup-dead-letter-worker}")
public class RocketMqDocumentStorageCleanupDeadLetterConsumer implements RocketMQListener<MessageExt> {

    private static final String FAILURE_REASON = "文档对象存储清理任务进入RocketMQ死信队列";

    private final ObjectMapper objectMapper;
    private final DocumentTaskFinalFailureService finalFailureService;

    @Override
    public void onMessage(MessageExt messageExt) {
        DocumentStorageCleanupMessage message = deserialize(messageExt);
        int consumeRetryCount = Math.max(messageExt.getReconsumeTimes() + 1, 1);

        // 1. 在同一事务中标记最终失败并创建告警，失败时由死信消息重投
        finalFailureService.markFailedAndCreateAlerts(message.outboxId(), consumeRetryCount, FAILURE_REASON);
        log.error("文档对象存储清理任务进入死信队列，outboxId={}，documentId={}，operationId={}",
                message.outboxId(), message.documentId(), message.operationId());
    }

    private DocumentStorageCleanupMessage deserialize(MessageExt messageExt) {
        try {
            return objectMapper.readValue(messageExt.getBody(), DocumentStorageCleanupMessage.class);
        } catch (Exception exception) {
            throw new ServiceException("解析文档对象存储清理死信消息失败，messageId=" + messageExt.getMsgId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
