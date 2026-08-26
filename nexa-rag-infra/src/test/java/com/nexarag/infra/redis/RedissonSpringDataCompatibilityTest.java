package com.nexarag.infra.redis;

import org.junit.jupiter.api.Test;
import org.redisson.spring.data.connection.RedissonConnection;
import org.springframework.data.redis.connection.ExpirationOptions;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redisson 与 Spring Data Redis 的接口兼容性测试。
 */
class RedissonSpringDataCompatibilityTest {

    /**
     * Spring Data Redis 3.5 的过期操作需要 Redisson 实现带条件的 PEXPIRE。
     *
     * @throws NoSuchMethodException 当前 Redisson 适配器未实现该方法时抛出
     */
    @Test
    void shouldImplementConditionalPExpireRequiredBySpringDataRedis() throws NoSuchMethodException {
        Method method = RedissonConnection.class.getDeclaredMethod("pExpire", byte[].class, long.class,
                ExpirationOptions.Condition.class);

        assertThat(method.getDeclaringClass()).isEqualTo(RedissonConnection.class);
    }
}
