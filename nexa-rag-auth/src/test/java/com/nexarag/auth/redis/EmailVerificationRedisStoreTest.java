package com.nexarag.auth.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邮箱验证码 Redis 状态机脚本返回值映射测试。
 */
class EmailVerificationRedisStoreTest {

    /**
     * 验证脚本确认预占后返回不可预测的预占令牌。
     */
    @Test
    void shouldCreateReservationWhenVerificationScriptReservesChallenge() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(1L);
        EmailVerificationRedisStore store = new EmailVerificationRedisStore(redisTemplate);

        EmailChallengeReservation reservation = store.reserve(12L, "context-hash", "code-hash");

        assertThat(reservation).isNotNull();
        assertThat(reservation.challengeId()).isEqualTo(12L);
        assertThat(reservation.reservationToken()).isNotBlank();
    }

    /**
     * 验证错误次数达到上限时不再提供预占令牌。
     */
    @Test
    void shouldRejectReservationWhenVerificationAttemptsReachLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(-3L);
        EmailVerificationRedisStore store = new EmailVerificationRedisStore(redisTemplate);

        assertThat(store.reserve(12L, "context-hash", "wrong-code-hash")).isNull();
    }

    /**
     * 验证创建挑战时将当前上下文和验证码哈希交由原子脚本写入。
     */
    @Test
    void shouldCreateChallengeWithAtomicRedisScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(1L);
        EmailVerificationRedisStore store = new EmailVerificationRedisStore(redisTemplate);

        boolean created = store.create(12L, "context-hash", "code-hash", LocalDateTime.now().plusMinutes(5));

        assertThat(created).isTrue();
        verify(redisTemplate).execute(any(), anyList(), any(Object[].class));
    }

    /**
     * 验证提交和回滚均以预占令牌作为条件，避免迟到事务影响新挑战。
     */
    @Test
    void shouldFinalizeOrReleaseOnlyByReservationToken() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        EmailVerificationRedisStore store = new EmailVerificationRedisStore(redisTemplate);
        EmailChallengeReservation reservation = new EmailChallengeReservation(12L, "reservation-token");

        store.consumeAfterCommit(reservation, "context-hash");
        store.releaseAfterRollback(reservation, "context-hash");

        verify(redisTemplate, org.mockito.Mockito.times(2)).execute(any(), anyList(), any(Object[].class));
    }
}
