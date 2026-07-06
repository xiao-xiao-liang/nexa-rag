package com.nexarag.model.refresh.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.model.enums.ModelRefreshChannel;
import com.nexarag.model.refresh.ModelRefreshMessageClient;
import com.nexarag.model.refresh.ModelRegistryChangedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis PubSub 模型注册表刷新消息客户端，负责发布注册表变更消息。
 */
@RequiredArgsConstructor
public class RedisModelRegistryRefreshMessageClient implements ModelRefreshMessageClient {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ModelRefreshChannel channel() {
        return ModelRefreshChannel.PUB_SUB;
    }

    @Override
    public void publish(String topic, ModelRegistryChangedMessage message) {
        try {
            // 1. 将刷新消息序列化为 JSON，保证跨进程传输格式稳定
            String payload = objectMapper.writeValueAsString(message);

            // 2. 发布到 Redis PubSub 指定主题
            redisTemplate.convertAndSend(topic, payload);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("序列化模型注册表刷新消息失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
