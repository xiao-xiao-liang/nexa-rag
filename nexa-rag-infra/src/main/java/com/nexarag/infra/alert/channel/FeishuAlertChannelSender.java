package com.nexarag.infra.alert.channel;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.alert.model.AlertChannel;
import com.nexarag.infra.alert.model.AlertMessage;
import com.nexarag.infra.config.AlertProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 飞书机器人告警发送器，使用Webhook投递脱敏动态卡片消息。
 */
@Component
@RequiredArgsConstructor
public class FeishuAlertChannelSender implements AlertChannelSender {

    private final RestClient alertRestClient;
    private final AlertProperties properties;
    private final FeishuAlertCardTemplate cardTemplate = new FeishuAlertCardTemplate();

    @Override
    public AlertChannel channel() {
        return AlertChannel.FEISHU;
    }

    @Override
    public void send(AlertMessage message) {
        // 1. 校验渠道开关和Webhook，防止向空地址或未授权渠道投递
        AlertProperties.FeishuProperties feishu = properties.getFeishu();
        if (!properties.isEnabled() || !feishu.isEnabled()) {
            throw new ServiceException("飞书告警渠道未启用");
        }
        if (feishu.getWebhookUrl() == null || feishu.getWebhookUrl().isBlank()) {
            throw new ServiceException("飞书告警Webhook未配置");
        }

        // 2. 使用飞书机器人 interactive 协议发送已脱敏的任务失败卡片
        try {
            alertRestClient.post()
                    .uri(feishu.getWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("msg_type", "interactive", "card", cardTemplate.render(message)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("发送飞书告警失败，outboxId=" + message.outboxId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

}
