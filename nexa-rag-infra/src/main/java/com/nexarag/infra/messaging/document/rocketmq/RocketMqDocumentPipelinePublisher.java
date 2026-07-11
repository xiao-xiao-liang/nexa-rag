package com.nexarag.infra.messaging.document.rocketmq;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.messaging.document.DocumentPipelineMessagePublisher;
import com.nexarag.infra.messaging.document.config.DocumentPipelineMessagingProperties;
import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import com.nexarag.infra.messaging.document.model.DocumentPipelinePublishResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 文档流水线消息发布器，负责构建消息Key并同步发送文档任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.document.pipeline.messaging", name = "type", havingValue = "rocketmq")
@ConditionalOnProperty(prefix = "nexa.document.pipeline.messaging", name = "publish-mode", havingValue = "outbox")
public class RocketMqDocumentPipelinePublisher implements DocumentPipelineMessagePublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final DocumentPipelineMessagingProperties properties;

    /**
     * 同步发布文档流水线消息。
     *
     * @param message 文档流水线消息
     * @return 发布成功结果
     * @throws ServiceException RocketMQ 返回异常结果或调用失败时抛出
     */
    @Override
    public DocumentPipelinePublishResult publish(DocumentPipelineMessage message) {
        // 1. 校验并构建 RocketMQ 消息
        if (message == null) {
            throw new ServiceException("文档流水线消息不能为空");
        }
        String messageKey = message.documentId() + ":" + message.processId();
        Message<DocumentPipelineMessage> rocketMqMessage = MessageBuilder.withPayload(message)
                .setHeader(RocketMQHeaders.KEYS, messageKey)
                .build();

        try {
            // 2. 使用配置主题同步发布消息
            SendResult sendResult = rocketMQTemplate.syncSend(properties.getTopic(), rocketMqMessage);

            // 3. 校验 RocketMQ 返回结果
            validateSendResult(sendResult, message);

            // 4. 返回发布成功结果
            log.info("文档流水线消息发布成功，documentId={}，processId={}，messageId={}",
                    message.documentId(), message.processId(), sendResult.getMsgId());
            return DocumentPipelinePublishResult.success(sendResult.getMsgId());
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("调用 RocketMQ 发布文档流水线消息异常，documentId={}，processId={}",
                    message.documentId(), message.processId(), exception);
            throw new ServiceException("调用 RocketMQ 发布文档流水线消息异常，documentId=" + message.documentId()
                    + "，processId=" + message.processId(), exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 校验 RocketMQ 同步发送结果。
     *
     * @param sendResult 同步发送结果
     * @param message 文档流水线消息
     * @throws ServiceException 发送结果为空或发送状态非成功时抛出
     */
    private void validateSendResult(SendResult sendResult, DocumentPipelineMessage message) {
        if (sendResult == null) {
            throw new ServiceException("文档流水线消息发布失败，发送结果为空，documentId=" + message.documentId()
                    + "，processId=" + message.processId());
        }
        if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
            throw new ServiceException("文档流水线消息发布失败，发送状态=" + sendResult.getSendStatus()
                    + "，documentId=" + message.documentId() + "，processId=" + message.processId());
        }
        if (sendResult.getMsgId() == null || sendResult.getMsgId().isBlank()) {
            throw new ServiceException("文档流水线消息发布失败，消息ID为空，documentId=" + message.documentId()
                    + "，processId=" + message.processId());
        }
    }
}
