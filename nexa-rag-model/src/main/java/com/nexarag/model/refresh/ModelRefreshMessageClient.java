package com.nexarag.model.refresh;

import com.nexarag.model.enums.ModelRefreshChannel;

/**
 * 模型刷新消息客户端端口，由 MQ 或 Redis PubSub 适配器实现。
 */
public interface ModelRefreshMessageClient {

    /**
     * 返回客户端支持的刷新通道。
     *
     * @return 刷新通道
     */
    ModelRefreshChannel channel();

    /**
     * 发布刷新消息。
     *
     * @param topic   消息主题
     * @param message 刷新消息
     */
    void publish(String topic, ModelRegistryChangedMessage message);
}
