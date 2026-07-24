package com.nexarag.model.prompt.refresh.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.prompt.refresh.PromptReleaseChangedMessage;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Redis Prompt 刷新消息客户端测试。
 */
class RedisPromptRefreshMessageClientTest {

    /**
     * 验证 Redis 不可用时不向发布事务后回调传播异常。
     */
    @Test
    void shouldIgnoreRedisConnectionFailureWhenPublishingMessage() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisPromptRefreshMessageClient client = new RedisPromptRefreshMessageClient(redisTemplate, new ObjectMapper());
        PromptReleaseChangedMessage message = new PromptReleaseChangedMessage("chat.answer", 9L, 4L);
        doThrow(new RedisConnectionFailureException("Redis不可用"))
                .when(redisTemplate).convertAndSend("nexa.prompt.release.changed", "{\"promptCode\":\"chat.answer\",\"releaseId\":9,\"releaseRevision\":4}");

        // 1. Redis 发布连接失败时仍完成当前实例的发布后处理。
        assertThatCode(() -> client.publish("nexa.prompt.release.changed", message)).doesNotThrowAnyException();
    }
}
