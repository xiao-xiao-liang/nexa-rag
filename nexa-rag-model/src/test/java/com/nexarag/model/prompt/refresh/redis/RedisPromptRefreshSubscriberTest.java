package com.nexarag.model.prompt.refresh.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.toolkits.prompt.PromptReleaseReconciler;
import com.nexarag.model.prompt.refresh.PromptReleaseChangedMessage;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.SubscriptionListener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Redis Prompt 刷新消息订阅器测试。
 */
class RedisPromptRefreshSubscriberTest {

    /**
     * 验证 Redis 监听容器重新订阅频道时会立即执行发布代次对账。
     */
    @Test
    void shouldReconcileWhenRedisChannelIsResubscribed() {
        PromptReleaseReconciler reconciler = mock(PromptReleaseReconciler.class);
        RedisPromptRefreshSubscriber subscriber = new RedisPromptRefreshSubscriber(new ObjectMapper(), reconciler);

        // 1. 模拟监听容器在 Redis 连接恢复后重新订阅频道。
        ((SubscriptionListener) subscriber).onChannelSubscribed("nexa.prompt.release.changed".getBytes(), 1L);

        // 2. 验证真实订阅回调立即触发发布代次对账。
        verify(reconciler).reconcile();
    }

    /**
     * 验证订阅连接建立或重连后立即执行发布代次对账。
     */
    @Test
    void shouldReconcileImmediatelyWhenRedisSubscriptionIsEstablished() {
        PromptReleaseReconciler reconciler = mock(PromptReleaseReconciler.class);
        RedisPromptRefreshSubscriber subscriber = new RedisPromptRefreshSubscriber(new ObjectMapper(), reconciler);

        // 1. 模拟 Redis Pub/Sub 订阅连接建立或重连。
        subscriber.onSubscribed();

        // 2. 验证立即执行发布代次对账。
        verify(reconciler).reconcile();
    }

    /**
     * 验证有效消息交给发布代次对账器处理。
     */
    @Test
    void shouldForwardReleaseChangedMessageToReconciler() throws Exception {
        PromptReleaseReconciler reconciler = mock(PromptReleaseReconciler.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RedisPromptRefreshSubscriber subscriber = new RedisPromptRefreshSubscriber(objectMapper, reconciler);
        PromptReleaseChangedMessage message = new PromptReleaseChangedMessage("chat.answer", 9L, 4L);

        // 1. 接收 Redis 发布的 JSON 消息。
        subscriber.onMessage(objectMapper.writeValueAsString(message));

        // 2. 验证消息被转交给对账器进行幂等失效。
        verify(reconciler).onReleaseChanged(message);
    }
}
