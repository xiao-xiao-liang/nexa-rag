package com.nexarag.retrieval.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentTaskAlertService;
import com.nexarag.infra.messaging.document.task.DocumentTaskMessage;
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
@RocketMQMessageListener(topic = "%DLQ%nexa-document-index-cleanup-worker",
        consumerGroup = "nexa-document-index-cleanup-dead-letter-worker")
public class DocumentIndexCleanupDeadLetterConsumer implements RocketMQListener<MessageExt> {

    private static final String FAILURE_REASON = "文档索引清理任务进入RocketMQ死信队列";

    private final ObjectMapper objectMapper;
    private final DocumentPipelineOutboxService outboxService;
    private final DocumentTaskAlertService taskAlertService;

    @Override
    public void onMessage(MessageExt messageExt) {
        DocumentTaskMessage message = deserialize(messageExt);
        int consumeRetryCount = Math.max(messageExt.getReconsumeTimes() + 1, 1);

        // 1. 仅在父清理任务首次进入FAILED时创建渠道告警，避免死信重复投递产生重复任务
        boolean markedFailed = outboxService.markTaskFailed(message.outboxId(), consumeRetryCount, FAILURE_REASON);
        if (!markedFailed) {
            return;
        }
        taskAlertService.createFailureAlerts(message.outboxId(), consumeRetryCount, FAILURE_REASON);
        log.error("文档索引清理任务进入死信队列，outboxId={}，documentId={}，operationId={}",
                message.outboxId(), message.documentId(), message.operationId());
    }

    private DocumentTaskMessage deserialize(MessageExt messageExt) {
        try {
            return objectMapper.readValue(messageExt.getBody(), DocumentTaskMessage.class);
        } catch (Exception exception) {
            throw new ServiceException("解析文档索引清理死信消息失败，messageId=" + messageExt.getMsgId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
