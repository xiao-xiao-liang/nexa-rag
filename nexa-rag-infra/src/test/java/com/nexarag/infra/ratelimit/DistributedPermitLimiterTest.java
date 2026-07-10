package com.nexarag.infra.ratelimit;

import org.junit.jupiter.api.Test;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分布式可过期许可限流器测试。
 */
class DistributedPermitLimiterTest {

    @Test
    void initializeShouldSetMaxPermits() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RPermitExpirableSemaphore semaphore = mock(RPermitExpirableSemaphore.class);
        when(redissonClient.getPermitExpirableSemaphore("nexa:test")).thenReturn(semaphore);
        DistributedPermitLimiter limiter = new DistributedPermitLimiter(redissonClient);

        limiter.initialize("nexa:test", 3);

        verify(semaphore).setPermits(3);
    }

    @Test
    void acquireShouldReturnPermitIdWhenSemaphoreAcquired() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RPermitExpirableSemaphore semaphore = mock(RPermitExpirableSemaphore.class);
        when(redissonClient.getPermitExpirableSemaphore("nexa:test")).thenReturn(semaphore);
        when(semaphore.tryAcquire(5, 60, TimeUnit.SECONDS)).thenReturn("permit-1");
        DistributedPermitLimiter limiter = new DistributedPermitLimiter(redissonClient);

        Optional<String> permitId = limiter.acquire("nexa:test", 5, 60);

        assertThat(permitId).contains("permit-1");
    }

    @Test
    void releaseShouldReleasePermitWhenPermitIdExists() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RPermitExpirableSemaphore semaphore = mock(RPermitExpirableSemaphore.class);
        when(redissonClient.getPermitExpirableSemaphore("nexa:test")).thenReturn(semaphore);
        DistributedPermitLimiter limiter = new DistributedPermitLimiter(redissonClient);

        limiter.release("nexa:test", "permit-1");

        verify(semaphore).tryRelease("permit-1");
    }
}
