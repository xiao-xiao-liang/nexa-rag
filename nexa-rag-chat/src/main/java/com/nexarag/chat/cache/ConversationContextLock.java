package com.nexarag.chat.cache;

import com.nexarag.chat.constants.ChatContextConstants;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.nexarag.chat.constants.ChatContextConstants.CACHE_KEY_PREFIX;

/**
 * 提供按用户和会话隔离的分布式锁。
 */
@Component
@RequiredArgsConstructor
public class ConversationContextLock {

    private final RedissonClient redissonClient;

    /**
     * 在会话锁内执行任务。
     *
     * @param userId 用户 ID
     * @param conversationId 会话 ID
     * @param action 锁内任务
     * @param <T> 返回值类型
     * @return 任务返回值
     */
    public <T> T execute(String userId, String conversationId, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey(userId, conversationId));
        boolean locked = false;
        try {
            // 使用 Redisson 看门狗自动续期，避免模型调用超过固定租约后锁提前失效
            locked = lock.tryLock(5, TimeUnit.SECONDS);
            if (!locked) {
                throw new IllegalStateException("获取会话上下文锁超时");
            }
            return action.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("获取会话上下文锁被中断", exception);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 在会话锁内执行无返回值任务。
     */
    public void execute(String userId, String conversationId, Runnable action) {
        execute(userId, conversationId, () -> {
            action.run();
            return null;
        });
    }

    private String lockKey(String userId, String conversationId) {
        return CACHE_KEY_PREFIX + "lock:" + userId + ":" + conversationId;
    }
}
