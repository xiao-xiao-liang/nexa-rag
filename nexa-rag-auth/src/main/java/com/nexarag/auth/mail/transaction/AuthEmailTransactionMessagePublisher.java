package com.nexarag.auth.mail.transaction;

import com.nexarag.auth.config.AuthMailTransactionProperties;
import com.nexarag.auth.mail.AuthMailMessageCipher;
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
 * 认证邮件 RocketMQ 事务消息发布器，保证本地挑战创建成功后才允许消费者发送邮件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEmailTransactionMessagePublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final AuthMailTransactionProperties properties;
    private final AuthMailMessageCipher messageCipher;

    /**
     * 发送认证邮件半消息，并由本地事务监听器决定提交或回滚。
     *
     * @param command 本地事务创建挑战命令
     */
    public void publish(CreateEmailChallengeCommand command) {
        if (command == null || command.challengeId() == null) {
            throw new ServiceException("认证邮件事务消息参数不能为空");
        }
        AuthEmailVerificationMessage payload = new AuthEmailVerificationMessage(command.challengeId(),
                messageCipher.encrypt(command.email()), command.purpose(), messageCipher.encrypt(command.verificationCode()));
        Message<AuthEmailVerificationMessage> message = MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, String.valueOf(command.challengeId()))
                .build();
        try {
            rocketMQTemplate.sendMessageInTransaction(properties.getTopic(), message, command);
            log.info("认证邮件事务消息已提交，challengeId={}，purpose={}", command.challengeId(), command.purpose());
        } catch (Exception exception) {
            log.error("认证邮件事务消息发送失败，challengeId={}，purpose={}", command.challengeId(), command.purpose(),
                    exception);
            throw new ServiceException("认证邮件事务消息发送失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
