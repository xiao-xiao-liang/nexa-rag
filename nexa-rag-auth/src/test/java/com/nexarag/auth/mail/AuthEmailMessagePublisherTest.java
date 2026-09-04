package com.nexarag.auth.mail;

import com.nexarag.auth.config.AuthMailProperties;
import com.nexarag.auth.enums.EmailVerificationPurpose;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 普通认证邮件消息发布测试。 */
class AuthEmailMessagePublisherTest {

    @Test
    void shouldUseChallengeIdAsRocketMqMessageKey() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        AuthMailProperties properties = new AuthMailProperties();
        properties.setTopic("nexa-auth-email");
        AuthMailMessageCipher cipher = mock(AuthMailMessageCipher.class);
        AuthEmailMessagePublisher publisher = new AuthEmailMessagePublisher(template, properties, cipher);

        publisher.publish(100L, "user@example.com", EmailVerificationPurpose.EMAIL_LOGIN, "012345");

        org.mockito.ArgumentCaptor<Message<?>> captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(template).syncSend(eq("nexa-auth-email"), captor.capture());
        assertThat(captor.getValue().getHeaders().get(RocketMQHeaders.KEYS)).isEqualTo("100");
    }
}
