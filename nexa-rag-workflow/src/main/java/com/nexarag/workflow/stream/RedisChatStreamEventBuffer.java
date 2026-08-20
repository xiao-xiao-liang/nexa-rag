package com.nexarag.workflow.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.workflow.constants.ChatGenerationRedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 基于 Redis ZSET 和 Pub/Sub 的可恢复 Chat 流事件缓冲。
 */
@Component
@RequiredArgsConstructor
public class RedisChatStreamEventBuffer implements ChatStreamEventBuffer {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 分配全局于生成任务的版本，持久化事件后通知其他实例。
     *
     * @param event 待发布事件
     * @return 带版本的已发布事件
     */
    @Override
    public ChatStreamEvent publish(ChatStreamEvent event) {
        validateGenerationId(event.generationId());
        // 1. 使用 Redis 自增序列保证多实例下事件版本单调递增
        long eventVersion = requireVersion(redisTemplate.opsForValue().increment(sequenceKey(event.generationId())));
        ChatStreamEvent persistedEvent = event.withEventVersion(eventVersion);
        String payload = serialize(persistedEvent);

        // 2. 先写可恢复缓冲，再发送非持久化实时通知
        redisTemplate.opsForZSet().add(bufferKey(event.generationId()), payload, eventVersion);
        trimBuffer(event.generationId());
        refreshExpiry(event.generationId());
        redisTemplate.convertAndSend(topic(event.generationId()), payload);
        return persistedEvent;
    }

    /**
     * 读取指定版本之后的缓冲事件，并按事件版本升序返回。
     *
     * @param generationId 生成任务 ID
     * @param eventVersion 已接收版本
     * @return 可重放事件
     */
    @Override
    public List<ChatStreamEvent> eventsAfter(String generationId, long eventVersion) {
        validateGenerationId(generationId);
        Set<String> payloads = redisTemplate.opsForZSet().rangeByScore(bufferKey(generationId),
                Math.max(0L, eventVersion) + Double.MIN_VALUE, Double.MAX_VALUE);
        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }
        return payloads.stream()
                .map(this::deserialize)
                .filter(event -> event.eventVersion() > eventVersion)
                .sorted(Comparator.comparingLong(ChatStreamEvent::eventVersion))
                .toList();
    }

    private void trimBuffer(String generationId) {
        redisTemplate.opsForZSet().removeRange(bufferKey(generationId), 0,
                -ChatGenerationRedisConstants.MAX_BUFFERED_EVENTS - 1);
    }

    private void refreshExpiry(String generationId) {
        redisTemplate.expire(sequenceKey(generationId), ChatGenerationRedisConstants.TASK_TTL);
        redisTemplate.expire(bufferKey(generationId), ChatGenerationRedisConstants.TASK_TTL);
    }

    private long requireVersion(Long version) {
        if (version == null || version <= 0) {
            throw new IllegalStateException("无法分配生成流事件版本");
        }
        return version;
    }

    private String serialize(ChatStreamEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("生成流事件序列化失败", exception);
        }
    }

    private ChatStreamEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, ChatStreamEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("生成流事件反序列化失败", exception);
        }
    }

    private void validateGenerationId(String generationId) {
        if (generationId == null || generationId.isBlank()) {
            throw new IllegalArgumentException("生成任务ID不能为空");
        }
    }

    private String sequenceKey(String generationId) {
        return ChatGenerationRedisConstants.EVENT_SEQUENCE_KEY_PREFIX + generationId;
    }

    private String bufferKey(String generationId) {
        return ChatGenerationRedisConstants.EVENT_BUFFER_KEY_PREFIX + generationId;
    }

    private String topic(String generationId) {
        return ChatGenerationRedisConstants.EVENT_TOPIC_PREFIX + generationId;
    }
}
