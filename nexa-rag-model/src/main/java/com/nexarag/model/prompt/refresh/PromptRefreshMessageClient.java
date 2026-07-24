package com.nexarag.model.prompt.refresh;

/**
 * Prompt 刷新消息客户端端口，隔离 Redis 等消息基础设施。
 */
public interface PromptRefreshMessageClient {

    /**
     * 向指定主题发布 Prompt 刷新消息。
     *
     * @param topic   消息主题
     * @param message 刷新消息
     */
    void publish(String topic, PromptReleaseChangedMessage message);
}
