package com.nexarag.infra.alert;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.alert.channel.AlertChannelSender;
import com.nexarag.infra.alert.model.AlertChannel;
import com.nexarag.infra.alert.model.AlertMessage;
import com.nexarag.infra.alert.model.AlertSeverity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警分发器测试，验证消息只会交给对应渠道。
 */
class AlertDispatcherTest {

    @Test
    void shouldDispatchToMatchedChannel() {
        AlertChannelSender feishuSender = mock(AlertChannelSender.class);
        when(feishuSender.channel()).thenReturn(AlertChannel.FEISHU);
        AlertDispatcher dispatcher = new AlertDispatcher(List.of(feishuSender));

        dispatcher.dispatch(message(AlertChannel.FEISHU));

        verify(feishuSender).send(message(AlertChannel.FEISHU));
    }

    @Test
    void shouldRejectMessageWhenChannelIsNotConfigured() {
        AlertChannelSender feishuSender = mock(AlertChannelSender.class);
        when(feishuSender.channel()).thenReturn(AlertChannel.FEISHU);
        AlertDispatcher dispatcher = new AlertDispatcher(List.of(feishuSender));

        assertThatThrownBy(() -> dispatcher.dispatch(message(AlertChannel.EMAIL)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未配置告警渠道");
    }

    private AlertMessage message(AlertChannel channel) {
        return new AlertMessage(11L, 7L, 3L, "operation-1", "CLEAN_DOCUMENT_INDEX",
                AlertSeverity.ERROR, channel, "索引清理失败", 5,
                LocalDateTime.of(2026, 8, 7, 18, 0));
    }
}
