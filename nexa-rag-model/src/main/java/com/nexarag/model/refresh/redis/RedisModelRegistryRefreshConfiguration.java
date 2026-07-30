package com.nexarag.model.refresh.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.config.ModelRegistryRefreshProperties;
import com.nexarag.model.refresh.ModelRegistryChangeListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * Redis PubSub 模型注册表刷新配置，负责注册发布端和订阅监听容器。
 */
@Configuration
@ConditionalOnProperty(prefix = "nexa.model.registry", name = "refresh-channel", havingValue = "REDIS_PUB_SUB")
public class RedisModelRegistryRefreshConfiguration {

    /**
     * 注册 Redis PubSub 刷新消息发布客户端。
     *
     * @param redisTemplate Redis 字符串模板
     * @param objectMapper  JSON 序列化器
     * @return Redis PubSub 刷新消息发布客户端
     */
    @Bean
    public RedisModelRegistryRefreshMessageClient redisModelRegistryRefreshMessageClient(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        return new RedisModelRegistryRefreshMessageClient(redisTemplate, objectMapper);
      }

    /**
     * 注册 Redis PubSub 刷新消息订阅器。
     *
     * @param objectMapper                JSON 序列化器
     * @param modelRegistryChangeListener 模型注册表变更监听器
     * @return Redis PubSub 刷新消息订阅器
     */
    @Bean
    public RedisModelRegistryRefreshSubscriber redisModelRegistryRefreshSubscriber(
            ObjectMapper objectMapper,
            ModelRegistryChangeListener modelRegistryChangeListener) {
        return new RedisModelRegistryRefreshSubscriber(objectMapper, modelRegistryChangeListener);
    }

    /**
     * 注册 Redis PubSub 消息监听容器。
     *
     * @param redisConnectionFactory Redis 连接工厂
     * @param subscriber             Redis 刷新消息订阅器
     * @param properties             Model 注册表刷新配置
     * @return Redis PubSub 消息监听容器
     */
    @Bean
    public RedisMessageListenerContainer modelRegistryRefreshRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            RedisModelRegistryRefreshSubscriber subscriber,
            ModelRegistryRefreshProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener((message, pattern) -> {
            // 1. 将 Redis 原始字节消息转换为字符串后交给订阅器处理
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            subscriber.onMessage(payload);
        }, new ChannelTopic(properties.getRefreshTopic()));
        return container;
    }
}
