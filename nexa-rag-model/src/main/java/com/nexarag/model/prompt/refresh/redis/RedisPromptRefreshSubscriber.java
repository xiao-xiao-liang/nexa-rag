package com.nexarag.model.prompt.refresh.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.toolkits.prompt.PromptReleaseReconciler;
import com.nexarag.model.prompt.refresh.PromptReleaseChangedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.SubscriptionListener;

import java.nio.charset.StandardCharsets;

/**
 * Redis Pub/Sub Prompt 刷新消息订阅器，负责转交消息和订阅恢复后的立即对账。
 */
@Slf4j
@RequiredArgsConstructor
public class RedisPromptRefreshSubscriber implements MessageListener, SubscriptionListener {

    private final ObjectMapper objectMapper;
    private final PromptReleaseReconciler reconciler;

    /**
     * 处理 Redis 推送的 Prompt 刷新消息。
     *
     * @param payload Redis Pub/Sub 消息正文
     */
    public void onMessage(String payload) {
        try {
            // 1. 将 JSON 消息反序列化为发布代次事件。
            PromptReleaseChangedMessage message = objectMapper.readValue(payload, PromptReleaseChangedMessage.class);
            // 2. 交由对账器按代次幂等处理。
            reconciler.onReleaseChanged(message);
        } catch (JsonProcessingException exception) {
            log.warn("解析 Prompt 刷新消息失败，payload={}", payload, exception);
        } catch (Exception exception) {
            log.warn("处理 Prompt 刷新消息失败，payload={}", payload, exception);
        }
    }

    /**
     * 接收 Redis 监听容器分发的原始消息。
     *
     * @param message Redis 原始消息
     * @param pattern  订阅模式
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        // 1. 按 UTF-8 将 Redis 原始字节转换为 JSON 文本。
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        // 2. 复用统一解析逻辑处理发布变更消息。
        onMessage(payload);
    }

    /**
     * 在 Redis Pub/Sub 订阅建立或重连后立即执行一次发布代次对账。
     */
    public void onSubscribed() {
        // 1. Redis Pub/Sub 不保证离线消息投递，因此连接恢复后立即补偿对账。
        reconciler.reconcile();
    }

    /**
     * 在 Redis 连接恢复后，监听容器重新订阅频道时立即触发对账。
     *
     * @param channel 已订阅频道
     * @param count   当前订阅数量
     */
    @Override
    public void onChannelSubscribed(byte[] channel, long count) {
        // 1. 容器首次订阅和重连后重新订阅都会进入该回调。
        // 2. 立即对账以补偿连接中断期间可能遗漏的发布消息。
        onSubscribed();
    }
}
