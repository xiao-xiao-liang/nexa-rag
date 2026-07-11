package com.nexarag.infra.messaging.document.model;

/**
 * 文档流水线消息发布结果，描述消息是否发布成功及对应消息ID或失败原因。
 *
 * @param success 是否发布成功
 * @param messageId 消息ID
 * @param failureReason 失败原因
 */
public record DocumentPipelinePublishResult(
        boolean success,
        String messageId,
        String failureReason) {
}
