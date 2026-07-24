package com.nexarag.model.prompt.refresh.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.config.PromptRefreshProperties;
import com.nexarag.model.toolkits.prompt.PromptReleaseReconciler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub Prompt 刷新配置，注册消息客户端和订阅监听容器。
 */
@Configuration
public class RedisPromptRefreshConfiguration {

    /**
     * 注册 Redis Prompt 刷新消息客户端。
     *
     * @param redisTemplate Redis 字符串模板
     * @param objectMapper  JSON 序列化器
     * @return Redis Prompt 刷新消息客户端
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public RedisPromptRefreshMessageClient redisPromptRefreshMessageClient(StringRedisTemplate redisTemplate,
                                                                            ObjectMapper objectMapper) {
        return new RedisPromptRefreshMessageClient(redisTemplate, objectMapper);
    }

    /**
     * 注册 Redis Prompt 刷新消息订阅器。
     *
     * @param objectMapper JSON 序列化器
     * @param reconciler   发布代次对账器
     * @return Redis Prompt 刷新消息订阅器
     */
    @Bean
    public RedisPromptRefreshSubscriber redisPromptRefreshSubscriber(ObjectMapper objectMapper,
                                                                       PromptReleaseReconciler reconciler) {
        return new RedisPromptRefreshSubscriber(objectMapper, reconciler);
    }

    /**
     * 注册 Redis Prompt 刷新消息监听容器。
     *
     * @param redisConnectionFactory Redis 连接工厂
     * @param subscriber             Prompt 刷新消息订阅器
     * @param properties             Prompt 刷新配置
     * @return Redis 消息监听容器
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisMessageListenerContainer promptRefreshRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            RedisPromptRefreshSubscriber subscriber,
            PromptRefreshProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        // 1. 将同时实现消息和订阅回调的订阅器注册到监听容器。
        container.addMessageListener(subscriber, new ChannelTopic(properties.getTopic()));
        // 2. 首次订阅和连接恢复后的重新订阅由 SubscriptionListener 立即触发对账。
        return container;
    }
}
