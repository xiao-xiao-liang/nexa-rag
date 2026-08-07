package com.nexarag.infra.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 通用告警投递配置，保存渠道启停、Topic 和非敏感路由信息。
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "nexa.alert")
public class AlertProperties {

    /** 是否启用告警投递。 */
    private boolean enabled;

    /** 告警消息Topic。 */
    @NotBlank(message = "告警消息Topic不能为空")
    private String topic = "nexa-alert";

    /** 告警消息消费者组。 */
    @NotBlank(message = "告警消息消费者组不能为空")
    private String consumerGroup = "nexa-alert-worker";

    /** 告警死信Topic。 */
    @NotBlank(message = "告警死信Topic不能为空")
    private String deadLetterTopic = "%DLQ%nexa-alert-worker";

    /** 告警死信消费者组。 */
    @NotBlank(message = "告警死信消费者组不能为空")
    private String deadLetterConsumerGroup = "nexa-alert-dead-letter-worker";

    /** 飞书渠道配置。 */
    private FeishuProperties feishu = new FeishuProperties();

    /** 邮件渠道配置。 */
    private EmailProperties email = new EmailProperties();

    /**
     * 飞书机器人配置。
     */
    @Getter
    @Setter
    public static class FeishuProperties {

        /** 是否启用飞书渠道。 */
        private boolean enabled;

        /** 飞书机器人Webhook地址。 */
        private String webhookUrl;
    }

    /**
     * 邮件渠道配置。
     */
    @Getter
    @Setter
    public static class EmailProperties {

        /** 是否启用邮件渠道。 */
        private boolean enabled;

        /** 邮件发件人地址。 */
        private String from;

        /** 邮件收件人，使用逗号分隔。 */
        private String recipients;
    }
}
