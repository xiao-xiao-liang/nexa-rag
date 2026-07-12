package com.nexarag.workflow.stream;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;

import static com.nexarag.workflow.constants.ChatGenerationRedisConstants.CANCEL_KEY_PREFIX;
import static com.nexarag.workflow.constants.ChatGenerationRedisConstants.CANCEL_TOPIC;
import static com.nexarag.workflow.constants.ChatGenerationRedisConstants.OWNER_KEY_PREFIX;
import static com.nexarag.workflow.constants.ChatGenerationRedisConstants.TASK_TTL;

/**
 * Chat 生成取消 Redis 协调器，负责发布跨实例取消标记和消息。
 */
@Component
@RequiredArgsConstructor
public class ChatGenerationCancellationHandler {

    private final StringRedisTemplate redisTemplate;
    private BiConsumer<String, String> cancellationListener = (generationId, userId) -> { };

    /**
     * 注册本实例的取消消息监听回调。
     *
     * @param listener 取消消息监听回调
     */
    public void registerListener(BiConsumer<String, String> listener) {
        this.cancellationListener = listener;
    }

    /**
     * 保存生成任务所属用户，供跨实例取消鉴权。
     *
     * @param generationId 生成任务 ID
     * @param userId 用户 ID
     */
    public void registerOwner(String generationId, String userId) {
        redisTemplate.opsForValue().set(OWNER_KEY_PREFIX + generationId, userId, TASK_TTL);
    }

    /**
     * 发布取消标记和 Pub/Sub 消息。
     *
     * @param generationId 生成任务 ID
     * @param userId 用户 ID
     */
    public void publishCancellation(String generationId, String userId) {
        // 1. 写入取消标记，覆盖订阅消息先于模型流绑定的竞态
        redisTemplate.opsForValue().set(CANCEL_KEY_PREFIX + generationId, userId, TASK_TTL);

        // 2. 发布跨实例取消通知
        redisTemplate.convertAndSend(CANCEL_TOPIC, generationId + ":" + userId);
    }

    /**
     * 查询生成任务所属用户。
     *
     * @param generationId 生成任务 ID
     * @return 用户 ID，不存在时返回 null
     */
    public String findOwner(String generationId) {
        return redisTemplate.opsForValue().get(OWNER_KEY_PREFIX + generationId);
    }

    /**
     * 判断任务是否已被取消。
     *
     * @param generationId 生成任务 ID
     * @return 是否存在取消标记
     */
    public boolean isCancelled(String generationId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(CANCEL_KEY_PREFIX + generationId));
    }

    /**
     * 处理 Redis Pub/Sub 取消消息。
     *
     * @param payload 取消消息，格式为 generationId:userId
     */
    public void onMessage(String payload) {
        int separator = payload.lastIndexOf(':');
        if (separator <= 0 || separator == payload.length() - 1) {
            return;
        }
        cancellationListener.accept(payload.substring(0, separator), payload.substring(separator + 1));
    }
}
