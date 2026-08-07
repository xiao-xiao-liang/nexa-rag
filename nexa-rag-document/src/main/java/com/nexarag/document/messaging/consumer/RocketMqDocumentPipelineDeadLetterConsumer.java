package com.nexarag.document.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.service.impl.DocumentProcessFailureService;
import com.nexarag.infra.messaging.document.model.DocumentPipelineFailureMessage;
import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * RocketMQ文档流水线死信消费者，负责将自动重试耗尽的消息转为最终失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.document.pipeline.messaging", name = "type", havingValue = "rocketmq")
@RocketMQMessageListener(
        topic = "%DLQ%${nexa.document.pipeline.messaging.consumer-group:nexa-document-pipeline-worker}",
        consumerGroup = "${nexa.document.pipeline.messaging.failure-consumer-group:nexa-document-pipeline-failure-handler}-dlq")
public class RocketMqDocumentPipelineDeadLetterConsumer implements RocketMQListener<MessageExt> {

    private final ObjectMapper objectMapper;
    private final DocumentProcessFailureService failureService;

    @Override
    public void onMessage(MessageExt messageExt) {
        try {
            // 1. 将重试耗尽的原始消息转换为统一失败消息
            DocumentPipelineMessage message = objectMapper.readValue(messageExt.getBody(), DocumentPipelineMessage.class);
            int consumedTimes = Math.max(messageExt.getReconsumeTimes(), 1);
            DocumentPipelineFailureMessage failureMessage = new DocumentPipelineFailureMessage(
                    message.documentId(), message.processId(), "ROCKETMQ_RETRY_EXHAUSTED",
                    "RocketMQ自动重试已达上限", "消息进入死信队列",
                    consumedTimes, messageExt.getMsgId(), LocalDateTime.now());

            // 2. 复用最终失败事务和告警逻辑
            failureService.markFinalFailure(failureMessage);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("处理文档流水线死信消息失败，messageId={}", messageExt.getMsgId(), exception);
            throw new ServiceException("处理文档流水线死信消息失败，messageId=" + messageExt.getMsgId());
        }
    }
}
