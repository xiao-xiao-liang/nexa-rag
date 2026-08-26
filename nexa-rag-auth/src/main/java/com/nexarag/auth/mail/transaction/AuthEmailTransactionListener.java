package com.nexarag.auth.mail.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * 认证邮件事务监听器，在 RocketMQ 半消息阶段提交或回滚本地验证码挑战事务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQTransactionListener(rocketMQTemplateBeanName = "rocketMQTemplate")
public class AuthEmailTransactionListener implements RocketMQLocalTransactionListener {

    private final EmailChallengeTransactionService challengeTransactionService;

    /**
     * 执行验证码挑战本地事务，失败时回滚半消息。
     *
     * @param message RocketMQ 半消息
     * @param argument 发送端透传的本地事务命令
     * @return 本地事务结果
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object argument) {
        if (!(argument instanceof CreateEmailChallengeCommand command)) {
            log.error("认证邮件事务消息缺少本地事务命令");
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        try {
            challengeTransactionService.createChallenge(command);
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception exception) {
            log.error("认证邮件本地事务执行失败，challengeId={}", command.challengeId(), exception);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    /**
     * 根据挑战记录回查半消息的本地事务状态。
     *
     * @param message Broker 回查消息
     * @return 已创建挑战则提交，否则回滚
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        Long challengeId = parseChallengeId(message);
        return challengeTransactionService.exists(challengeId)
                ? RocketMQLocalTransactionState.COMMIT : RocketMQLocalTransactionState.ROLLBACK;
    }

    private Long parseChallengeId(Message message) {
        Object key = message == null ? null : message.getHeaders().get(RocketMQHeaders.KEYS);
        if (key == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(key));
        } catch (NumberFormatException exception) {
            log.warn("认证邮件事务消息挑战ID格式非法，key={}", key);
            return null;
        }
    }
}
