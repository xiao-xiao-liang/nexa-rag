package com.nexarag.model.prompt.refresh;

import com.nexarag.model.config.PromptRefreshProperties;
import com.nexarag.model.toolkits.prompt.PromptSnapshotCache;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Prompt 刷新消息发布器测试。
 */
class PromptRefreshPublisherTest {

    /**
     * 验证发布前先精确失效当前实例缓存，再发送 Redis 刷新消息。
     */
    @Test
    void shouldInvalidateLocalCacheBeforePublishingMessage() {
        PromptSnapshotCache cache = spy(new PromptSnapshotCache());
        PromptRefreshMessageClient messageClient = mock(PromptRefreshMessageClient.class);
        PromptRefreshProperties properties = new PromptRefreshProperties();
        PromptRefreshPublisher publisher = new PromptRefreshPublisher(cache, messageClient, properties);
        PromptReleaseChangedMessage message = new PromptReleaseChangedMessage("chat.answer", 9L, 4L);

        // 1. 发布已提交事务对应的刷新消息。
        publisher.publish(message);

        // 2. 验证本机缓存先被精确删除，随后才向配置主题发送消息。
        verify(messageClient).publish(properties.getTopic(), message);
        InOrder inOrder = inOrder(cache, messageClient);
        inOrder.verify(cache).invalidateCurrent("chat.answer");
        inOrder.verify(messageClient).publish(properties.getTopic(), message);
    }

}
