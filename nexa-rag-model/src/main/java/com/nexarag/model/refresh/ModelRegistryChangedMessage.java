package com.nexarag.model.refresh;

import com.nexarag.model.enums.ModelRefreshChannel;

/**
 * 模型注册表变更消息。
 *
 * @param versionNo 全局模型注册表版本号
 * @param channel   刷新通道
 */
public record ModelRegistryChangedMessage(long versionNo, ModelRefreshChannel channel) {

    /**
     * 创建模型注册表变更消息，默认使用 Redis Pub/Sub 通道。
     *
     * @param versionNo 全局模型注册表版本号
     */
    public ModelRegistryChangedMessage(long versionNo) {
        this(versionNo, ModelRefreshChannel.REDIS_PUB_SUB);
    }
}
