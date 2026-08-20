package com.nexarag.workflow.config;

import com.nexarag.workflow.stream.ChatGenerationCancellationHandler;
import com.nexarag.workflow.stream.ChatGenerationEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

import static com.nexarag.workflow.constants.ChatGenerationRedisConstants.CANCEL_TOPIC;
import static com.nexarag.workflow.constants.ChatGenerationRedisConstants.EVENT_TOPIC_PREFIX;

/**
 * Chat 生成任务跨实例取消监听配置。
 */
@Configuration
public class ChatGenerationCancellationConfiguration {

    /**
     * 创建 Chat 取消消息监听容器。
     *
     * @param redisConnectionFactory Redis 连接工厂
     * @param cancellationHandler 取消消息处理器
     * @return Redis 消息监听容器
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisMessageListenerContainer chatGenerationCancellationListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            ChatGenerationCancellationHandler cancellationHandler,
            ChatGenerationEventPublisher eventPublisher) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener((message, pattern) -> {
            // 1. 将 Redis 原始消息转换为字符串并交给取消处理器
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            cancellationHandler.onMessage(payload);
        }, new ChannelTopic(CANCEL_TOPIC));
        container.addMessageListener((message, pattern) -> {
            // 2. 将生成事件转交给本实例 SSE 分发器
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            eventPublisher.acceptRedisPayload(payload);
        }, new PatternTopic(EVENT_TOPIC_PREFIX + "*"));
        return container;
    }
}
