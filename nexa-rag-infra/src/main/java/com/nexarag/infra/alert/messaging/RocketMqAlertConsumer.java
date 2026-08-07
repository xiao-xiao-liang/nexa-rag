package com.nexarag.infra.alert.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.alert.AlertDeliveryLifecycle;
import com.nexarag.infra.alert.AlertDispatcher;
import com.nexarag.infra.alert.model.AlertMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * RocketMQ告警正常消费者，负责投递外部渠道并同步业务任务状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(AlertDeliveryLifecycle.class)
@RocketMQMessageListener(topic = "${nexa.alert.topic:nexa-alert}",
        consumerGroup = "${nexa.alert.consumer-group:nexa-alert-worker}", maxReconsumeTimes = 5)
public class RocketMqAlertConsumer implements RocketMQListener<MessageExt> {

    private final AlertDeliveryLifecycle lifecycle;
    private final AlertDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt messageExt) {
        AlertMessage message = deserialize(messageExt);
        int consumeRetryCount = Math.max(messageExt.getReconsumeTimes() + 1, 1);

        // 1. 领取业务任务，已进入终态的重复消息直接确认
        if (!lifecycle.markProcessing(message, consumeRetryCount)) {
            return;
        }

        // 2. 渠道异常继续抛出，交由RocketMQ执行有限重试
        dispatcher.dispatch(message);
        lifecycle.markSucceeded(message);
        log.info("告警任务投递成功，outboxId={}，parentOutboxId={}，channel={}，severity={}",
                message.outboxId(), message.parentOutboxId(), message.channel(), message.severity());
    }

    private AlertMessage deserialize(MessageExt messageExt) {
        try {
            return objectMapper.readValue(messageExt.getBody(), AlertMessage.class);
        } catch (Exception exception) {
            throw new ServiceException("解析告警消息失败，messageId=" + messageExt.getMsgId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
