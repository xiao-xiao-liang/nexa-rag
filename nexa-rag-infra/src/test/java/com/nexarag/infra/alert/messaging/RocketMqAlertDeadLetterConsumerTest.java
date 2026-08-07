package com.nexarag.infra.alert.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.infra.alert.AlertDeliveryLifecycle;
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
 * 告警死信消费者测试，验证失败仅记录自身状态而不递归发送新告警。
 */
class RocketMqAlertDeadLetterConsumerTest {

    @Test
    void shouldMarkAlertFailedWithoutRedispatching() throws Exception {
        AlertDeliveryLifecycle lifecycle = mock(AlertDeliveryLifecycle.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        AlertMessage message = message();
        when(objectMapper.readValue(any(byte[].class), eq(AlertMessage.class))).thenReturn(message);
        RocketMqAlertDeadLetterConsumer consumer = new RocketMqAlertDeadLetterConsumer(lifecycle, objectMapper);

        consumer.onMessage(messageExt(5));

        verify(lifecycle).markFailed(eq(message), eq(6), any(String.class));
    }

    private MessageExt messageExt(int reconsumeTimes) {
        MessageExt messageExt = new MessageExt();
        messageExt.setBody("{}".getBytes(StandardCharsets.UTF_8));
        messageExt.setReconsumeTimes(reconsumeTimes);
        return messageExt;
    }

    private AlertMessage message() {
        return new AlertMessage(11L, 7L, 3L, "operation-1", "CLEAN_DOCUMENT_INDEX",
                AlertSeverity.ERROR, AlertChannel.EMAIL, "索引清理失败", 5,
                LocalDateTime.of(2026, 8, 7, 18, 0));
    }
}
