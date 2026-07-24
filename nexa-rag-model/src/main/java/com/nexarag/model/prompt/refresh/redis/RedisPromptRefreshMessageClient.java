package com.nexarag.model.prompt.refresh.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.model.prompt.refresh.PromptRefreshMessageClient;
import com.nexarag.model.prompt.refresh.PromptReleaseChangedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis Pub/Sub Prompt 刷新消息客户端。
 */
@Slf4j
@RequiredArgsConstructor
public class RedisPromptRefreshMessageClient implements PromptRefreshMessageClient {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 将刷新消息序列化为 JSON 并发布到 Redis 主题。
     *
     * @param topic   消息主题
     * @param message 刷新消息
     */
    @Override
    public void publish(String topic, PromptReleaseChangedMessage message) {
        try {
            // 1. 使用 JSON 固化跨实例消息格式。
            String payload = objectMapper.writeValueAsString(message);
            try {
                // 2. 向指定 Redis Pub/Sub 主题发送轻量消息。
                redisTemplate.convertAndSend(topic, payload);
            } catch (DataAccessException exception) {
                // 3. Redis 不可用时由定时对账补偿其他实例的缓存失效。
                log.warn("发布 Prompt 刷新消息失败，将等待定时对账补偿，topic={}，promptCode={}，releaseId={}，releaseRevision={}",
                        topic, message.promptCode(), message.releaseId(), message.releaseRevision(), exception);
            }
        } catch (JsonProcessingException exception) {
            throw new ServiceException("序列化 Prompt 刷新消息失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
