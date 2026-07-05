package com.nexarag.model.enums;

/**
 * 模型注册表刷新通道枚举。
 */
public enum ModelRefreshChannel {

    /**
     * 通过统一 MQ 适配发布刷新消息。
     */
    MQ,

    /**
     * 通过 Redis PubSub 发布刷新消息。
     */
    PUB_SUB
}
