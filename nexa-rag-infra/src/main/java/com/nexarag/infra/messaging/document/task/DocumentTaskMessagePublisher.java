package com.nexarag.infra.messaging.document.task;

/**
 * 文档任务消息发布接口，支持由 Outbox 决定目标 Topic 和消息键。
 */
public interface DocumentTaskMessagePublisher {

    /**
     * 同步发布文档任务消息。
     *
     * @param topic      RocketMQ Topic
     * @param messageKey 消息唯一键
     * @param payload    保持原始类型的消息体
     * @return 发布成功结果
     */
    DocumentMessagePublishResult publish(String topic, String messageKey, Object payload);
}
