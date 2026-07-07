package com.nexarag.model.enums;

/**
 * 模型注册表刷新通道枚举。
 */
public enum ModelRefreshChannel {

    /**
     * 仅刷新当前 JVM 实例。
     */
    LOCAL,

    /**
     * 通过 Redis Pub/Sub 发布刷新消息。
     */
    REDIS_PUB_SUB,

    /**
     * 通过 infra MQ 适配发布刷新消息，当前阶段仅预留。
     */
    INFRA_MQ
}
