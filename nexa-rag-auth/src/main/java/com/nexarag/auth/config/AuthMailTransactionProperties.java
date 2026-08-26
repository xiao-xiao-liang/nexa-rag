package com.nexarag.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证邮件 RocketMQ 事务消息配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nexa.auth.mail.transaction")
public class AuthMailTransactionProperties {

    /** 认证邮件事务消息主题。 */
    private String topic = "nexa-auth-email";

    /** 认证邮件消费者组。 */
    private String consumerGroup = "nexa-auth-email-worker";

    /** Base64 编码 AES 主密钥，仅通过环境变量注入。 */
    private String messageMasterKey;
}
