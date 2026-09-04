package com.nexarag.auth.mail;

import com.nexarag.auth.config.AuthMailProperties;
import com.nexarag.auth.enums.EmailVerificationPurpose;
import com.nexarag.auth.mail.message.AuthEmailVerificationMessage;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 认证验证码普通邮件消息发布器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEmailMessagePublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final AuthMailProperties properties;
    private final AuthMailMessageCipher messageCipher;

    /**
     * 发布已创建挑战对应的认证邮件消息。
     */
    public void publish(Long challengeId, String email, EmailVerificationPurpose purpose, String verificationCode) {
        if (challengeId == null || email == null || purpose == null || verificationCode == null) {
            throw new ServiceException("认证邮件消息参数不能为空");
        }
        AuthEmailVerificationMessage payload = new AuthEmailVerificationMessage(challengeId,
                messageCipher.encrypt(email), purpose, messageCipher.encrypt(verificationCode));
        Message<AuthEmailVerificationMessage> message = MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, String.valueOf(challengeId)).build();
        try {
            rocketMQTemplate.syncSend(properties.getTopic(), message);
        } catch (Exception exception) {
            log.error("认证邮件消息发送失败，challengeId={}，purpose={}", challengeId, purpose, exception);
            throw new ServiceException("认证邮件消息发送失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
