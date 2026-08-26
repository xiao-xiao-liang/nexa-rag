package com.nexarag.auth.mail.transaction;

import com.nexarag.auth.constants.EmailVerificationConstants;
import com.nexarag.auth.mapper.EmailVerificationChallengeMapper;
import com.nexarag.auth.model.dataobject.EmailVerificationChallengeDO;
import com.nexarag.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 邮箱验证码挑战本地事务服务，供 RocketMQ 事务监听器调用。
 */
@Service
@RequiredArgsConstructor
public class EmailChallengeTransactionService {

    private final EmailVerificationChallengeMapper challengeMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 在数据库本地事务中作废旧挑战并创建新挑战及 Redis 验证码哈希。
     *
     * @param command 创建挑战命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void createChallenge(CreateEmailChallengeCommand command) {
        // 1. 作废同邮箱同用途的历史未消费挑战
        challengeMapper.invalidateActiveByEmailKeyAndPurpose(command.emailKey(), command.purpose().name(),
                command.createTime());

        // 2. 持久化挑战元数据，验证码本身不写入数据库
        int inserted = challengeMapper.insert(new EmailVerificationChallengeDO(command.challengeId(), command.userId(),
                command.emailKey(), command.purpose().name(), command.contextHash(), command.expiresTime(), 0,
                null, null, command.createTime()));
        if (inserted != 1) {
            throw new ServiceException("创建邮箱验证码挑战失败，challengeId=" + command.challengeId());
        }

        // 3. Redis 仅保存带服务端密钥的验证码哈希，消息提交后消费者才可发送邮件
        redisTemplate.opsForValue().set(challengeKey(command.challengeId()), command.verificationCodeHash(),
                Duration.between(command.createTime(), command.expiresTime()));
    }

    /**
     * 判断本地事务是否已经成功提交，供 Broker 回查半消息状态。
     *
     * @param challengeId 挑战 ID
     * @return 已提交时返回 true
     */
    public boolean exists(Long challengeId) {
        return challengeId != null && challengeMapper.selectById(challengeId) != null;
    }

    private String challengeKey(Long challengeId) {
        return EmailVerificationConstants.CHALLENGE_KEY_PREFIX + challengeId;
    }
}
