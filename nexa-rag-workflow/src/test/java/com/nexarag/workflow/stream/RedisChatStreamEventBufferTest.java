package com.nexarag.workflow.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 流事件缓冲测试。
 */
class RedisChatStreamEventBufferTest {

    @Test
    void shouldAssignVersionPersistEventAndPublishIt() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(valueOperations.increment(anyString())).thenReturn(2L);

        RedisChatStreamEventBuffer buffer = new RedisChatStreamEventBuffer(redisTemplate, new ObjectMapper());
        ChatStreamEvent persisted = buffer.publish(new ChatStreamEvent(ChatStreamEventType.ANSWER_DELTA,
                "正文", "c1", "t1", "g1", "m1", null, null));

        assertThat(persisted.eventVersion()).isEqualTo(2L);
        assertThat(persisted.content()).isEqualTo("正文");
        verify(zSetOperations).add(anyString(), anyString(), anyDouble());
        verify(redisTemplate).convertAndSend(anyString(), anyString());
    }

    @Test
    void shouldReadOnlyEventsAfterRequestedVersion() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        ObjectMapper objectMapper = new ObjectMapper();
        String first = objectMapper.writeValueAsString(new ChatStreamEvent(ChatStreamEventType.SNAPSHOT,
                null, "c1", "t1", "g1", "m1", null, null).withEventVersion(2L));
        String second = objectMapper.writeValueAsString(new ChatStreamEvent(ChatStreamEventType.COMPLETE,
                null, "c1", "t1", "g1", "m1", null, null).withEventVersion(3L));
        when(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Set.of(first, second));

        RedisChatStreamEventBuffer buffer = new RedisChatStreamEventBuffer(redisTemplate, objectMapper);

        assertThat(buffer.eventsAfter("g1", 1L)).extracting(ChatStreamEvent::eventVersion)
                .containsExactly(2L, 3L);
    }
}
