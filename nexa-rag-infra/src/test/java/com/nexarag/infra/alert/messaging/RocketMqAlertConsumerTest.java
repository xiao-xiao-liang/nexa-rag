package com.nexarag.infra.alert.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.infra.alert.AlertDeliveryLifecycle;
import com.nexarag.infra.alert.AlertDispatcher;
import com.nexarag.infra.alert.model.AlertChannel;
import com.nexarag.infra.alert.model.AlertMessage;
import com.nexarag.infra.alert.model.AlertSeverity;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警正常消费者测试，验证成功投递后的任务状态流转。
 */
class RocketMqAlertConsumerTest {

    @Test
    void shouldMarkSucceededAfterDispatchingAlert() throws Exception {
        AlertDeliveryLifecycle lifecycle = mock(AlertDeliveryLifecycle.class);
        AlertDispatcher dispatcher = mock(AlertDispatcher.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        AlertMessage message = message();
        when(objectMapper.readValue(any(byte[].class), eq(AlertMessage.class))).thenReturn(message);
        when(lifecycle.markProcessing(message, 1)).thenReturn(true);
        RocketMqAlertConsumer consumer = new RocketMqAlertConsumer(lifecycle, dispatcher, objectMapper);

        consumer.onMessage(messageExt(0));

        verify(dispatcher).dispatch(message);
        verify(lifecycle).markSucceeded(message);
    }

    private MessageExt messageExt(int reconsumeTimes) {
        MessageExt messageExt = new MessageExt();
        messageExt.setBody("{}".getBytes(StandardCharsets.UTF_8));
        messageExt.setReconsumeTimes(reconsumeTimes);
        return messageExt;
    }

    private AlertMessage message() {
        return new AlertMessage(11L, 7L, 3L, "operation-1", "CLEAN_DOCUMENT_INDEX",
                AlertSeverity.ERROR, AlertChannel.FEISHU, "索引清理失败", 5,
                LocalDateTime.of(2026, 8, 7, 18, 0));
    }
}
