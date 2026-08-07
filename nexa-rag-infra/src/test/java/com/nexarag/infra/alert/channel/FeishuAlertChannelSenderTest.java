package com.nexarag.infra.alert.channel;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.alert.model.AlertChannel;
import com.nexarag.infra.alert.model.AlertMessage;
import com.nexarag.infra.alert.model.AlertSeverity;
import com.nexarag.infra.config.AlertProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 飞书告警渠道测试，验证未配置渠道不会被静默忽略。
 */
class FeishuAlertChannelSenderTest {

    @Test
    void shouldRejectSendingWhenFeishuChannelIsDisabled() {
        AlertProperties properties = new AlertProperties();
        properties.getFeishu().setEnabled(false);
        FeishuAlertChannelSender sender = new FeishuAlertChannelSender(RestClient.create(), properties);

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("飞书告警渠道未启用");
    }

    private AlertMessage message() {
        return new AlertMessage(11L, 7L, 3L, "operation-1", "CLEAN_DOCUMENT_INDEX",
                AlertSeverity.ERROR, AlertChannel.FEISHU, "索引清理失败", 5,
                LocalDateTime.of(2026, 8, 7, 18, 0));
    }
}
