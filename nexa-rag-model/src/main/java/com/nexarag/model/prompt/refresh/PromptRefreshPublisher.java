package com.nexarag.model.prompt.refresh;

import com.nexarag.model.config.PromptRefreshProperties;
import com.nexarag.model.toolkits.prompt.PromptSnapshotCache;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Prompt 刷新消息发布器，保证本机缓存失效先于跨实例通知。
 */
@Service
@RequiredArgsConstructor
public class PromptRefreshPublisher {

    private final PromptSnapshotCache snapshotCache;
    @Nullable
    private final PromptRefreshMessageClient messageClient;
    private final PromptRefreshProperties properties;

    /**
     * 精确失效本机当前发布缓存后，再向 Redis Pub/Sub 发布通知。
     *
     * @param message 已提交发布事务对应的变更消息
     */
    public void publish(PromptReleaseChangedMessage message) {
        // 1. 先删除本机当前发布缓存，避免发布实例继续读取旧快照。
        snapshotCache.invalidateCurrent(message.promptCode());
        // 2. Redis 不可用时保留本机失效结果，由发布代次对账补偿其他实例。
        if (messageClient != null) {
            messageClient.publish(properties.getTopic(), message);
        }
    }
}
