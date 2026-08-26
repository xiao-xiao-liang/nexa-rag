package com.nexarag.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexarag.auth.constants.EmailVerificationConstants;
import com.nexarag.auth.enums.EmailVerificationPurpose;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.mail.transaction.AuthEmailTransactionMessagePublisher;
import com.nexarag.auth.mail.transaction.CreateEmailChallengeCommand;
import com.nexarag.auth.mapper.EmailVerificationChallengeMapper;
import com.nexarag.auth.model.dataobject.EmailVerificationChallengeDO;
import com.nexarag.auth.model.vo.EmailChallengeVO;
import com.nexarag.auth.service.EmailChallengeService;
import com.nexarag.auth.service.EmailVerificationCodeHasher;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 使用 Redis 保存验证码哈希、使用数据库保存挑战元数据的邮箱验证码实现。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailChallengeServiceImpl implements EmailChallengeService {

    private static final ZoneId SHANGHAI_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private static final DefaultRedisScript<Long> RESERVE_SEND_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return -1
            end
            local dailyCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            if dailyCount >= tonumber(ARGV[1]) then
                return -2
            end
            redis.call('SET', KEYS[1], '1', 'EX', ARGV[2])
            if dailyCount == 0 then
                redis.call('SET', KEYS[2], '1', 'EX', ARGV[3])
            else
                redis.call('INCR', KEYS[2])
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SEND_SCRIPT = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1])
            local dailyCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            if dailyCount <= 1 then
                redis.call('DEL', KEYS[2])
            else
                redis.call('DECR', KEYS[2])
            end
            return 1
            """, Long.class);

    private final EmailVerificationChallengeMapper challengeMapper;
    private final StringRedisTemplate redisTemplate;
    private final AuthEmailTransactionMessagePublisher transactionMessagePublisher;
    private final EmailVerificationCodeHasher verificationCodeHasher;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * {@inheritDoc}
     */
    @Override
    public EmailChallengeVO sendCode(String email, EmailVerificationPurpose purpose, Long userId) {
        // 1. 规范化与校验邮箱、用途，避免将原始邮箱写入 Redis 键
        String normalizedEmail = normalizeEmail(email);
        requirePurpose(purpose);
        String contextHash = contextHash(normalizedEmail, purpose, userId);
        String keySuffix = digest(normalizedEmail);
        String resendKey = EmailVerificationConstants.RESEND_KEY_PREFIX + keySuffix + ':' + purpose.name();
        String dailyKey = EmailVerificationConstants.DAILY_COUNT_KEY_PREFIX + currentDateKey() + ':' + keySuffix;

        // 2. 原子占用重发间隔和日发送额度，额度按上海自然日计算
        reserveSendQuota(resendKey, dailyKey);
        try {
            String verificationCode = generateVerificationCode();
            Long challengeId = IdWorker.getId();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresTime = now.plusMinutes(EmailVerificationConstants.CODE_TTL_MINUTES);

            // 3. 发送 RocketMQ 事务消息；本地挑战事务提交成功后，消费者才会发送 SMTP 邮件
            transactionMessagePublisher.publish(new CreateEmailChallengeCommand(challengeId, userId, email.trim(),
                    normalizedEmail, purpose, contextHash, verificationCode,
                    verificationCodeHasher.hash(contextHash, verificationCode), expiresTime, now));
            log.info("认证邮箱验证码事务消息已提交，challengeId={}，purpose={}，email={}", challengeId, purpose.name(),
                    maskEmail(normalizedEmail));
            return new EmailChallengeVO(challengeId, expiresTime);
        } catch (RuntimeException exception) {
            releaseSendQuota(resendKey, dailyKey);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(noRollbackFor = ClientException.class)
    public void verifyAndConsume(Long challengeId, String email, EmailVerificationPurpose purpose, Long userId,
                                 String verificationCode) {
        // 1. 读取挑战元数据并验证上下文；最终消费由条件更新原子裁决
        String normalizedEmail = normalizeEmail(email);
        requirePurpose(purpose);
        if (challengeId == null) {
            throw invalidCode();
        }
        EmailVerificationChallengeDO challenge = challengeMapper.selectById(challengeId);
        LocalDateTime now = LocalDateTime.now();
        String contextHash = contextHash(normalizedEmail, purpose, userId);
        if (!isActiveChallenge(challenge, normalizedEmail, purpose, userId, contextHash, now)) {
            throw invalidCode();
        }

        // 2. 验证 Redis 中仅存的验证码哈希，失败时原子累计尝试次数
        String actualCodeHash = redisTemplate.opsForValue().get(challengeKey(challengeId));
        if (!verificationCodeHasher.matches(contextHash, verificationCode, actualCodeHash)) {
            challengeMapper.incrementVerifyAttemptsIfActive(challengeId, now,
                    EmailVerificationConstants.MAX_VERIFY_ATTEMPTS);
            throw invalidCode();
        }

        // 3. 完整上下文条件更新保证验证码只能成功消费一次
        int consumed = challengeMapper.consumeIfActive(challengeId, normalizedEmail, purpose.name(), userId,
                contextHash, now, EmailVerificationConstants.MAX_VERIFY_ATTEMPTS);
        if (consumed != 1) {
            throw invalidCode();
        }
        deleteChallengeHashAfterCommit(challengeId);
    }

    /**
     * 原子占用验证码发送频率和当日额度。
     */
    private void reserveSendQuota(String resendKey, String dailyKey) {
        Long result = redisTemplate.execute(RESERVE_SEND_SCRIPT, List.of(resendKey, dailyKey),
                String.valueOf(EmailVerificationConstants.MAX_DAILY_SEND_COUNT),
                String.valueOf(EmailVerificationConstants.RESEND_INTERVAL_SECONDS),
                String.valueOf(secondsUntilNextShanghaiDay()));
        if (Objects.equals(result, -1L)) {
            throw new ClientException(AuthErrorCode.EMAIL_CODE_SEND_TOO_FREQUENT);
        }
        if (Objects.equals(result, -2L)) {
            throw new ClientException(AuthErrorCode.EMAIL_CODE_DAILY_LIMIT_EXCEEDED);
        }
        if (!Objects.equals(result, 1L)) {
            throw new ClientException("验证码发送服务暂不可用");
        }
    }

    /**
     * 邮件投递或挑战创建失败时归还本次预留额度。
     */
    private void releaseSendQuota(String resendKey, String dailyKey) {
        redisTemplate.execute(RELEASE_SEND_SCRIPT, List.of(resendKey, dailyKey));
    }

    /**
     * 判断挑战是否属于当前请求并仍处于可验证状态。
     */
    private boolean isActiveChallenge(EmailVerificationChallengeDO challenge, String emailKey,
                                      EmailVerificationPurpose purpose, Long userId, String contextHash,
                                      LocalDateTime now) {
        return challenge != null
                && Objects.equals(challenge.getEmailKey(), emailKey)
                && Objects.equals(challenge.getPurposeCode(), purpose.name())
                && Objects.equals(challenge.getUserId(), userId)
                && isSameHash(contextHash, challenge.getContextHash())
                && challenge.getConsumedTime() == null
                && challenge.getInvalidatedTime() == null
                && challenge.getExpiresTime().isAfter(now)
                && challenge.getVerifyAttempts() != null
                && challenge.getVerifyAttempts() < EmailVerificationConstants.MAX_VERIFY_ATTEMPTS;
    }

    /**
     * 规范化并做最小邮箱格式校验。
     */
    private String normalizeEmail(String email) {
        if (email == null) {
            throw invalidCode();
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        int atIndex = normalizedEmail.lastIndexOf('@');
        if (normalizedEmail.length() > 320 || atIndex <= 0 || atIndex == normalizedEmail.length() - 1
                || normalizedEmail.indexOf(' ') >= 0) {
            throw invalidCode();
        }
        return normalizedEmail;
    }

    /**
     * 拒绝缺失用途，确保挑战不会成为通用验证码。
     */
    private void requirePurpose(EmailVerificationPurpose purpose) {
        if (purpose == null) {
            throw invalidCode();
        }
    }

    /**
     * 生成六位数字验证码，保留前导零。
     */
    private String generateVerificationCode() {
        return String.format("%0" + EmailVerificationConstants.CODE_LENGTH + "d",
                secureRandom.nextInt((int) Math.pow(10, EmailVerificationConstants.CODE_LENGTH)));
    }

    /**
     * 生成绑定邮箱、用途和用户主体的上下文哈希。
     */
    private String contextHash(String emailKey, EmailVerificationPurpose purpose, Long userId) {
        return digest((userId == null ? "anonymous" : userId) + "|" + purpose.name() + '|' + emailKey);
    }

    /**
     * 使用 SHA-256 生成十六进制摘要。
     */
    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 未提供 SHA-256 算法", exception);
        }
    }

    /**
     * 常量时间比较两个哈希值。
     */
    private boolean isSameHash(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取当前上海自然日键。
     */
    private String currentDateKey() {
        return ZonedDateTime.now(SHANGHAI_ZONE_ID).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    /**
     * 计算日额度键距离下一个上海自然日的存活秒数。
     */
    private long secondsUntilNextShanghaiDay() {
        ZonedDateTime now = ZonedDateTime.now(SHANGHAI_ZONE_ID);
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(SHANGHAI_ZONE_ID)).getSeconds();
    }

    /**
     * 构造挑战验证码的 Redis 键。
     */
    private String challengeKey(Long challengeId) {
        return EmailVerificationConstants.CHALLENGE_KEY_PREFIX + challengeId;
    }

    /**
     * 仅在数据库消费记录提交后删除 Redis 验证材料，允许多验证码事务整体回滚。
     */
    private void deleteChallengeHashAfterCommit(Long challengeId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            redisTemplate.delete(challengeKey(challengeId));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisTemplate.delete(challengeKey(challengeId));
            }
        });
    }

    /**
     * 脱敏邮箱地址，避免安全日志泄露完整个人信息。
     */
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        return atIndex <= 1 ? "***" : email.charAt(0) + "***" + email.substring(atIndex);
    }

    /**
     * 返回不暴露挑战内部状态的统一验证码异常。
     */
    private ClientException invalidCode() {
        return new ClientException(AuthErrorCode.EMAIL_CODE_INVALID);
    }
}
