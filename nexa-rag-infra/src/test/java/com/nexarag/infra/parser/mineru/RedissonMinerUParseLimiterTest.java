package com.nexarag.infra.parser.mineru;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.MinerUProperties;
import com.nexarag.infra.parser.mineru.ratelimit.RedissonMinerUParseLimiter;
import com.nexarag.infra.ratelimit.DistributedPermitLimiter;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redisson MinerU 解析限流器测试。
 */
class RedissonMinerUParseLimiterTest {

    @Test
    void executeShouldRunActionAndReleasePermitWhenAcquireSuccess() {
        MinerUProperties properties = buildProperties();
        DistributedPermitLimiter permitLimiter = mock(DistributedPermitLimiter.class);
        when(permitLimiter.acquire("nexa:mineru:parse", 3, 600)).thenReturn(Optional.of("permit-1"));
        RedissonMinerUParseLimiter limiter = new RedissonMinerUParseLimiter(properties, permitLimiter);

        String result = limiter.execute(1L, () -> "parsed");

        assertThat(result).isEqualTo("parsed");
        verify(permitLimiter).acquire("nexa:mineru:parse", 3, 600);
        verify(permitLimiter).release("nexa:mineru:parse", "permit-1");
    }

    @Test
    void executeShouldRejectWhenAcquirePermitTimeout() {
        MinerUProperties properties = buildProperties();
        DistributedPermitLimiter permitLimiter = mock(DistributedPermitLimiter.class);
        when(permitLimiter.acquire("nexa:mineru:parse", 3, 600)).thenReturn(Optional.empty());
        RedissonMinerUParseLimiter limiter = new RedissonMinerUParseLimiter(properties, permitLimiter);

        assertThatThrownBy(() -> limiter.execute(1L, () -> "parsed"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("MinerU解析任务过多");
        verify(permitLimiter, never()).release("nexa:mineru:parse", "permit-1");
    }

    private MinerUProperties buildProperties() {
        MinerUProperties properties = new MinerUProperties();
        properties.setSemaphoreName("nexa:mineru:parse");
        properties.setMaxWaitSeconds(3);
        properties.setLeaseSeconds(600);
        return properties;
    }
}
