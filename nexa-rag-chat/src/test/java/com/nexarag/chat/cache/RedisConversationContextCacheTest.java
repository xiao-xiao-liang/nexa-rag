package com.nexarag.chat.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.chat.domain.ConversationContext;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Redis 活跃上下文缓存的键和值行为。
 */
class RedisConversationContextCacheTest {

    @Test
    void shouldReadContextByUserScopedKey() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ConversationContext context = new ConversationContext("c1", "u1", "摘要", "m0", 0L, List.of(), "m1", 1L);
        when(valueOperations.get("nexa:chat:context:u1:c1:v1"))
                .thenReturn(new ObjectMapper().writeValueAsString(context));

        RedisConversationContextCache cache = new RedisConversationContextCache(redisTemplate, new ObjectMapper());

        Optional<ConversationContext> result = cache.get("u1", "c1");

        assertThat(result).isPresent().contains(context);
    }

    @Test
    void shouldWriteContextWithSlidingTtl() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ConversationContext context = new ConversationContext("c1", "u1", null, null, null, List.of(), null, 1L);
        RedisConversationContextCache cache = new RedisConversationContextCache(redisTemplate, new ObjectMapper());

        cache.put(context);

        verify(valueOperations).set(eq("nexa:chat:context:u1:c1:v1"), eq(new ObjectMapper().writeValueAsString(context)),
                eq(Duration.ofHours(24)));
    }
}
