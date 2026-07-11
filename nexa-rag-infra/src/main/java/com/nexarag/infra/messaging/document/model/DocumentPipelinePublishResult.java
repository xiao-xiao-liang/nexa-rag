package com.nexarag.infra.messaging.document.model;

/**
 * 文档流水线消息发布结果，仅表达已成功发布且具有有效消息ID的状态。
 */
public final class DocumentPipelinePublishResult {

    private final String messageId;

    private DocumentPipelinePublishResult(String messageId) {
        this.messageId = messageId;
    }

    /**
     * 创建消息发布成功结果。
     *
     * @param messageId 消息ID
     * @return 消息发布成功结果
     * @throws IllegalArgumentException 消息ID为空时抛出
     */
    public static DocumentPipelinePublishResult success(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("消息ID不能为空");
        }
        return new DocumentPipelinePublishResult(messageId);
    }

    /**
     * 返回发布成功状态。
     *
     * @return 固定返回 true
     */
    public boolean success() {
        return true;
    }

    /**
     * 返回 RocketMQ 消息ID。
     *
     * @return 消息ID
     */
    public String messageId() {
        return messageId;
    }

    /**
     * 返回失败原因；发布失败统一通过异常表达。
     *
     * @return 固定返回 null
     */
    public String failureReason() {
        return null;
    }
}
