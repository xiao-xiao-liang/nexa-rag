package com.nexarag.model.refresh.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.refresh.ModelRegistryChangeListener;
import com.nexarag.model.refresh.ModelRegistryChangedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis PubSub 模型注册表刷新消息订阅器，负责解析消息并通知注册表刷新监听器。
 */
@Slf4j
@RequiredArgsConstructor
public class RedisModelRegistryRefreshSubscriber {

    private final ObjectMapper objectMapper;
    private final ModelRegistryChangeListener modelRegistryChangeListener;

    /**
     * 处理 Redis PubSub 推送的刷新消息。
     *
     * @param payload Redis PubSub 消息内容
     */
    public void onMessage(String payload) {
        try {
            // 1. 反序列化 Redis 消息
            ModelRegistryChangedMessage message = objectMapper.readValue(payload, ModelRegistryChangedMessage.class);

            // 2. 通知模型注册表刷新监听器
            modelRegistryChangeListener.onMessage(message);
        } catch (JsonProcessingException exception) {
            log.warn("解析模型注册表刷新消息失败，payload={}", payload, exception);
        } catch (Exception exception) {
            log.warn("处理模型注册表刷新消息失败，payload={}", payload, exception);
        }
    }
}
