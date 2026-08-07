package com.nexarag.infra.alert.channel;

import com.nexarag.infra.config.AlertProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 邮件告警发送器上下文测试，验证邮件自动配置存在时发送器能够注册。
 */
class EmailAlertChannelSenderContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(EmailAlertChannelSenderConfiguration.class)
            .withPropertyValues("spring.mail.host=smtp.qq.com", "nexa.alert.email.enabled=true");

    @Test
    void shouldRegisterEmailSenderWhenMailSenderIsAutoConfigured() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(EmailAlertChannelSender.class));
    }

    /**
     * 邮件告警发送器测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AlertProperties.class)
    @Import(EmailAlertChannelSender.class)
    static class EmailAlertChannelSenderConfiguration {
    }
}
