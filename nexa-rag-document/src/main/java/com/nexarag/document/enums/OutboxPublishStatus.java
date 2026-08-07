package com.nexarag.document.enums;

/**
 * 文档流水线Outbox消息发布状态。
 */
public enum OutboxPublishStatus {

    /**
     * 待发布。
     */
    PENDING,

    /**
     * 发布中。
     */
    PUBLISHING,

    /**
     * 已发布。
     */
    PUBLISHED,

    /**
     * 发布失败。
     */
    FAILED
}
