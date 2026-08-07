package com.nexarag.infra.alert.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.alert.AlertDeliveryLifecycle;
import com.nexarag.infra.alert.model.AlertMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * RocketMQ告警死信消费者，只记录告警任务自身最终失败，禁止递归告警。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(AlertDeliveryLifecycle.class)
@RocketMQMessageListener(topic = "${nexa.alert.dead-letter-topic:%DLQ%nexa-alert-worker}",
        consumerGroup = "${nexa.alert.dead-letter-consumer-group:nexa-alert-dead-letter-worker}")
public class RocketMqAlertDeadLetterConsumer implements RocketMQListener<MessageExt> {

    private final AlertDeliveryLifecycle lifecycle;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt messageExt) {
        AlertMessage message = deserialize(messageExt);
        int consumeRetryCount = Math.max(messageExt.getReconsumeTimes() + 1, 1);

        // 1. 将该渠道任务标记为最终失败，不创建新的Outbox或消息
        String failureReason = "告警消息进入RocketMQ死信队列";
        lifecycle.markFailed(message, consumeRetryCount, failureReason);

        // 2. 保留结构化错误日志供日志平台检索和人工处置
        log.error("告警任务进入死信队列，outboxId={}，parentOutboxId={}，channel={}，severity={}，failureReason={}",
                message.outboxId(), message.parentOutboxId(), message.channel(), message.severity(), failureReason);
    }

    private AlertMessage deserialize(MessageExt messageExt) {
        try {
            return objectMapper.readValue(messageExt.getBody(), AlertMessage.class);
        } catch (Exception exception) {
            throw new ServiceException("解析告警死信消息失败，messageId=" + messageExt.getMsgId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
