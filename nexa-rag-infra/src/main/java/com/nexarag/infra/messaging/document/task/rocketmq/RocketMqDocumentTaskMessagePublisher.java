package com.nexarag.infra.messaging.document.task.rocketmq;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.messaging.document.task.DocumentMessagePublishResult;
import com.nexarag.infra.messaging.document.task.DocumentTaskMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 文档任务通用发布器，根据 Outbox 提供的 Topic 和消息键发送原始消息对象。
 */
@Component
@RequiredArgsConstructor
public class RocketMqDocumentTaskMessagePublisher implements DocumentTaskMessagePublisher {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 同步发布任务消息并校验 Broker 返回结果。
     */
    @Override
    public DocumentMessagePublishResult publish(String topic, String messageKey, Object payload) {
        // 1. 校验发布定位字段，避免无法追踪的消息进入 Broker
        if (topic == null || topic.isBlank() || messageKey == null || messageKey.isBlank() || payload == null) {
            throw new ServiceException("文档任务消息发布参数不能为空");
        }
        Message<Object> message = MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, messageKey)
                .build();
        try {
            // 2. 同步发送并校验发送状态
            SendResult result = rocketMQTemplate.syncSend(topic, message);
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK
                    || result.getMsgId() == null || result.getMsgId().isBlank()) {
                throw new ServiceException("文档任务消息发布失败，topic=" + topic + "，messageKey=" + messageKey);
            }
            return new DocumentMessagePublishResult(result.getMsgId());
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("调用RocketMQ发布文档任务消息异常，topic=" + topic
                    + "，messageKey=" + messageKey, exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
