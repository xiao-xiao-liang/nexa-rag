package com.nexarag.retrieval.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.service.DocumentTaskFinalFailureService;
import com.nexarag.document.constants.DocumentMessagingConstants;
import com.nexarag.infra.messaging.document.task.DocumentTaskMessage;
import com.nexarag.infra.messaging.document.task.DocumentVersionIndexCleanupMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 索引清理任务死信消费者，标记父任务失败后创建独立渠道告警任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = DocumentMessagingConstants.INDEX_CLEANUP_DEAD_LETTER_TOPIC,
        consumerGroup = DocumentMessagingConstants.INDEX_CLEANUP_DEAD_LETTER_CONSUMER_GROUP)
public class DocumentIndexCleanupDeadLetterConsumer implements RocketMQListener<MessageExt> {

    private static final String FAILURE_REASON = "文档索引清理任务进入RocketMQ死信队列";

    private final ObjectMapper objectMapper;
    private final DocumentTaskFinalFailureService finalFailureService;

    @Override
    public void onMessage(MessageExt messageExt) {
        CleanupTaskMetadata message = deserialize(messageExt);
        int consumeRetryCount = Math.max(messageExt.getReconsumeTimes() + 1, 1);

        // 1. 在同一事务中标记最终失败并创建告警，失败时由死信消息重投
        finalFailureService.markFailedAndCreateAlerts(message.outboxId(), consumeRetryCount, FAILURE_REASON);
        log.error("文档索引清理任务进入死信队列，outboxId={}，documentId={}，operationId={}",
                message.outboxId(), message.documentId(), message.operationId());
    }

    private CleanupTaskMetadata deserialize(MessageExt messageExt) {
        try {
            if (isVersionCleanupMessage(messageExt)) {
                DocumentVersionIndexCleanupMessage message = objectMapper.readValue(messageExt.getBody(),
                        DocumentVersionIndexCleanupMessage.class);
                return new CleanupTaskMetadata(message.outboxId(), message.documentId(), message.operationId());
            }
            DocumentTaskMessage message = objectMapper.readValue(messageExt.getBody(), DocumentTaskMessage.class);
            return new CleanupTaskMetadata(message.outboxId(), message.documentId(), message.operationId());
        } catch (Exception exception) {
            throw new ServiceException("解析文档索引清理死信消息失败，messageId=" + messageExt.getMsgId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private boolean isVersionCleanupMessage(MessageExt messageExt) throws Exception {
        JsonNode messageNode = objectMapper.readTree(messageExt.getBody());
        return messageNode != null && messageNode.hasNonNull("documentVersionId");
    }

    private record CleanupTaskMetadata(Long outboxId, Long documentId, String operationId) {
    }
}
