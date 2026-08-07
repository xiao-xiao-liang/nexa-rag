package com.nexarag.infra.messaging.document.task;

/**
 * 文档任务消息发布成功结果。
 *
 * @param messageId RocketMQ消息ID
 */
public record DocumentMessagePublishResult(String messageId) {

    /**
     * 校验发布成功结果。
     */
    public DocumentMessagePublishResult {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("消息ID不能为空");
        }
    }
}
