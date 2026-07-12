package com.nexarag.chat.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.chat.constants.ChatContextConstants;
import com.nexarag.chat.domain.ConversationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

import static com.nexarag.chat.constants.ChatContextConstants.*;

/**
 * 基于 Redis JSON 字符串值的活跃会话上下文缓存实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisConversationContextCache implements ConversationContextCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<ConversationContext> get(String userId, String conversationId) {
        String key = key(userId, conversationId);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            refreshTtl(userId, conversationId);
            return Optional.of(objectMapper.readValue(value, ConversationContext.class));
        } catch (Exception exception) {
            log.warn("读取会话上下文缓存失败，将回源数据库: userId={}, conversationId={}", userId, conversationId, exception);
            return Optional.empty();
        }
    }

    @Override
    public void put(ConversationContext context) {
        String key = key(context.userId(), context.conversationId());
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(context), CACHE_TTL);
        } catch (JsonProcessingException exception) {
            log.warn("序列化会话上下文缓存失败: userId={}, conversationId={}", context.userId(), context.conversationId(), exception);
        } catch (RuntimeException exception) {
            log.warn("写入会话上下文缓存失败: userId={}, conversationId={}", context.userId(), context.conversationId(), exception);
        }
    }

    @Override
    public void evict(String userId, String conversationId) {
        try {
            redisTemplate.delete(key(userId, conversationId));
        } catch (RuntimeException exception) {
            log.warn("删除会话上下文缓存失败: userId={}, conversationId={}", userId, conversationId, exception);
        }
    }

    @Override
    public void refreshTtl(String userId, String conversationId) {
        try {
            redisTemplate.expire(key(userId, conversationId), CACHE_TTL);
        } catch (RuntimeException exception) {
            log.warn("刷新会话上下文缓存有效期失败: userId={}, conversationId={}", userId, conversationId, exception);
        }
    }

    private String key(String userId, String conversationId) {
        return CACHE_KEY_PREFIX + userId + ":" + conversationId + ":" + CACHE_KEY_VERSION;
    }
}
