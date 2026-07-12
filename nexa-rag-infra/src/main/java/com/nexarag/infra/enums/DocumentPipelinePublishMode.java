package com.nexarag.infra.enums;

/**
 * 文档流水线消息发布模式，用于选择消息可靠性策略。
 */
public enum DocumentPipelinePublishMode {

    /**
     * 通过本地消息表可靠发布消息。
     */
    OUTBOX
}
