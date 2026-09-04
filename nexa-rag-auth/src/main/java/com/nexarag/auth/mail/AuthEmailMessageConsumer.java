package com.nexarag.auth.mail;

import com.nexarag.auth.mail.message.AuthEmailVerificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 认证邮件普通消息消费者，负责投递 SMTP 邮件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "${nexa.auth.mail.topic}", consumerGroup = "${nexa.auth.mail.consumer-group}")
public class AuthEmailMessageConsumer implements RocketMQListener<AuthEmailVerificationMessage> {

    private final AuthMailMessageCipher messageCipher;
    private final AuthMailService authMailService;

    /**
     * 解密消息并发送验证码；异常继续抛出，由 RocketMQ 有限重试。
     *
     * @param message 认证邮件消息
     */
    @Override
    public void onMessage(AuthEmailVerificationMessage message) {
        if (message == null || message.challengeId() == null || message.purpose() == null) {
            throw new IllegalArgumentException("认证邮件消息不完整");
        }
        String email = messageCipher.decrypt(message.emailCiphertext());
        String verificationCode = messageCipher.decrypt(message.verificationCodeCiphertext());
        authMailService.sendVerificationCode(email, message.purpose(), verificationCode);
        log.info("认证邮件消息投递成功，challengeId={}，purpose={}", message.challengeId(), message.purpose());
    }
}
