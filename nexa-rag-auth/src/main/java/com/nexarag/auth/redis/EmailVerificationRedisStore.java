package com.nexarag.auth.redis;

import com.nexarag.auth.constants.EmailVerificationConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 邮箱验证码 Redis 状态机访问封装，负责调用原子 Lua 脚本。
 */
@Component
@RequiredArgsConstructor
public class EmailVerificationRedisStore {

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then return -1 end
            if redis.call('HGET', KEYS[1], 'state') ~= 'ACTIVE' then return -1 end
            if redis.call('HGET', KEYS[1], 'contextHash') ~= ARGV[2] then return -1 end
            if redis.call('HGET', KEYS[1], 'codeHash') ~= ARGV[3] then
                local attempts = redis.call('HINCRBY', KEYS[1], 'verifyAttempts', 1)
                if attempts >= tonumber(ARGV[5]) then redis.call('DEL', KEYS[1]); redis.call('DEL', KEYS[2]); return -3 end
                return -2
            end
            redis.call('HSET', KEYS[1], 'state', 'RESERVED', 'reservationToken', ARGV[4])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'state') ~= 'RESERVED' then return 0 end
            if redis.call('HGET', KEYS[1], 'reservationToken') ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[1]); redis.call('DEL', KEYS[2]); return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'state') ~= 'RESERVED' then return 0 end
            if redis.call('HGET', KEYS[1], 'reservationToken') ~= ARGV[1] then return 0 end
            redis.call('HSET', KEYS[1], 'state', 'ACTIVE'); redis.call('HDEL', KEYS[1], 'reservationToken'); return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> CREATE_SCRIPT = new DefaultRedisScript<>("""
            local previousId = redis.call('GET', KEYS[2])
            if previousId then
                redis.call('DEL', ARGV[1] .. previousId)
            end
            redis.call('HSET', KEYS[1], 'contextHash', ARGV[2], 'codeHash', ARGV[3],
                       'verifyAttempts', '0', 'state', 'ACTIVE')
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            redis.call('SET', KEYS[2], ARGV[5], 'EX', ARGV[4])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> INVALIDATE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[1]); redis.call('DEL', KEYS[2]); return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 校验并预占活动挑战。
     *
     * @param challengeId 挑战 ID
     * @param contextHash 当前请求上下文哈希
     * @param codeHash    当前输入验证码哈希
     * @return 预占成功时返回令牌；验证码无效时返回 null
     */
    public EmailChallengeReservation reserve(Long challengeId, String contextHash, String codeHash) {
        if (challengeId == null || contextHash == null || codeHash == null) {
            return null;
        }
        String reservationToken = nextReservationToken();
        Long result = redisTemplate.execute(RESERVE_SCRIPT, List.of(challengeKey(challengeId), activeKey(contextHash)),
                String.valueOf(challengeId), contextHash, codeHash, reservationToken,
                String.valueOf(EmailVerificationConstants.MAX_VERIFY_ATTEMPTS));
        return result == 1L ? new EmailChallengeReservation(challengeId, reservationToken) : null;
    }

    /**
     * 创建活动验证码挑战，并原子作废同一上下文的旧挑战。
     */
    public boolean create(Long challengeId, String contextHash, String codeHash, LocalDateTime expiresTime) {
        if (challengeId == null || contextHash == null || codeHash == null || expiresTime == null) {
            return false;
        }
        long ttlSeconds = Math.max(1L, Duration.between(LocalDateTime.now(), expiresTime).toSeconds());
        Long result = redisTemplate.execute(CREATE_SCRIPT, List.of(challengeKey(challengeId), activeKey(contextHash)),
                "auth:email:challenge:", contextHash, codeHash, String.valueOf(ttlSeconds), String.valueOf(challengeId));
        return result != null && result == 1L;
    }

    /** 提交数据库事务后消费预占挑战。 */
    public void consumeAfterCommit(EmailChallengeReservation reservation, String contextHash) {
        executeReservationScript(CONSUME_SCRIPT, reservation, contextHash);
    }

    /** 数据库事务回滚后释放预占挑战。 */
    public void releaseAfterRollback(EmailChallengeReservation reservation, String contextHash) {
        executeReservationScript(RELEASE_SCRIPT, reservation, contextHash);
    }

    /**
     * 作废仍是当前上下文活动挑战的验证码状态。
     *
     * @param challengeId 挑战 ID
     * @param contextHash 挑战上下文哈希
     */
    public void invalidateCurrent(Long challengeId, String contextHash) {
        if (challengeId == null || contextHash == null) {
            return;
        }
        redisTemplate.execute(INVALIDATE_SCRIPT, List.of(challengeKey(challengeId), activeKey(contextHash)),
                String.valueOf(challengeId));
    }

    private void executeReservationScript(DefaultRedisScript<Long> script, EmailChallengeReservation reservation,
                                          String contextHash) {
        if (reservation == null || contextHash == null) return;
        redisTemplate.execute(script, List.of(challengeKey(reservation.challengeId()), activeKey(contextHash)),
                reservation.reservationToken());
    }

    private String challengeKey(Long challengeId) {
        return EmailVerificationConstants.CHALLENGE_KEY_PREFIX + challengeId;
    }

    private String activeKey(String contextHash) {
        return EmailVerificationConstants.CHALLENGE_KEY_PREFIX + "active:" + contextHash;
    }

    private String nextReservationToken() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
