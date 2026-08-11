package com.nexarag.document.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.impl.DocumentProcessFailureService;
import com.nexarag.infra.messaging.document.DocumentPipelineMessageHandler;
import com.nexarag.infra.messaging.document.DocumentPipelineNonRetryableException;
import com.nexarag.infra.config.DocumentPipelineMessagingProperties;
import com.nexarag.infra.messaging.document.model.DocumentPipelineFailureMessage;
import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * RocketMQ文档流水线消费者，负责记录消费上下文并委托工作流处理消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.document.pipeline.messaging", name = "type", havingValue = "rocketmq")
@RocketMQMessageListener(
        topic = "${nexa.document.pipeline.messaging.topic:nexa-document-pipeline}",
        consumerGroup = "${nexa.document.pipeline.messaging.consumer-group:nexa-document-pipeline-worker}",
        maxReconsumeTimes = 5)
public class RocketMqDocumentPipelineConsumer implements RocketMQListener<MessageExt> {

    private static final int MAX_FAILURE_DETAIL_LENGTH = 4000;

    private final ObjectMapper objectMapper;
    private final DocumentService documentService;
    private final DocumentPipelineMessageHandler messageHandler;
    private final RocketMQTemplate rocketMQTemplate;
    private final DocumentPipelineMessagingProperties properties;
    private final DocumentProcessFailureService failureService;

    @Override
    public void onMessage(MessageExt messageExt) {
        DocumentPipelineMessage message = deserialize(messageExt);
        int consumedTimes = messageExt.getReconsumeTimes() + 1;

        // 1. 记录当前轮次消费上下文，旧轮次或终态消息直接确认
        boolean currentProcess = documentService.recordMessageConsumption(
                message.documentId(), message.processId(), messageExt.getMsgId(), consumedTimes);
        if (!currentProcess) {
            log.info("忽略旧轮次或终态文档消息，documentId={}，processId={}，messageId={}",
                    message.documentId(), message.processId(), messageExt.getMsgId());
            return;
        }

        try {
            // 2. 委托工作流执行当前文档处理轮次
            messageHandler.handle(message);
            documentService.markMessageCompleted(message.documentId(), message.processId());
        } catch (ClientException | DocumentPipelineNonRetryableException exception) {
            // 3. 永久性业务异常直接发布失败主题，避免无效重试
            publishFailure(message, messageExt, consumedTimes, currentFailureStage(message.documentId()), exception);
        } catch (RuntimeException exception) {
            // 4. 可重试异常先独立回写失败上下文，再交由 RocketMQ 重投
            failureService.recordFailure(message.documentId(), currentFailureStage(message.documentId()), exception.getMessage(),
                    truncate(exception.toString()));
            throw exception;
        }
    }

    private DocumentPipelineMessage deserialize(MessageExt messageExt) {
        try {
            return objectMapper.readValue(messageExt.getBody(), DocumentPipelineMessage.class);
        } catch (Exception exception) {
            log.error("反序列化文档流水线消息失败，messageId={}", messageExt.getMsgId(), exception);
            throw new ServiceException("反序列化文档流水线消息失败，messageId=" + messageExt.getMsgId());
        }
    }

    private void publishFailure(DocumentPipelineMessage message, MessageExt messageExt,
                                int consumedTimes, String failureStage, RuntimeException exception) {
        String failureDetail = truncate(exception.toString());
        DocumentPipelineFailureMessage failureMessage = new DocumentPipelineFailureMessage(
                message.outboxId(), message.documentId(), message.processId(), failureStage,
                exception.getMessage(), failureDetail, consumedTimes, messageExt.getMsgId(), LocalDateTime.now());
        org.springframework.messaging.Message<DocumentPipelineFailureMessage> rocketMqMessage =
                MessageBuilder.withPayload(failureMessage)
                        .setHeader(RocketMQHeaders.KEYS, message.documentId() + ":" + message.processId())
                        .build();
        try {
            SendResult result = rocketMQTemplate.syncSend(properties.getFailureTopic(), rocketMqMessage);
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                throw new ServiceException("发布文档流水线失败消息未成功，documentId=" + message.documentId());
            }
            log.warn("文档流水线不可重试异常已转入失败主题，documentId={}，processId={}，messageId={}",
                    message.documentId(), message.processId(), messageExt.getMsgId(), exception);
        } catch (RuntimeException publishException) {
            log.error("发布文档流水线失败消息异常，documentId={}，processId={}",
                    message.documentId(), message.processId(), publishException);
            throw publishException;
        }
    }

    /**
     * 获取异常发生时文档已推进到的处理状态，用作失败阶段。
     */
    private String currentFailureStage(Long documentId) {
        return documentService.getRequiredDocument(documentId).getStatus().name();
    }

    private String truncate(String detail) {
        if (detail == null || detail.length() <= MAX_FAILURE_DETAIL_LENGTH) {
            return detail;
        }
        return detail.substring(0, MAX_FAILURE_DETAIL_LENGTH);
    }
}
