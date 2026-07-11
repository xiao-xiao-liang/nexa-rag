package com.nexarag.document.enums;

/**
 * 文档流水线消息处理状态。
 */
public enum DocumentPipelineMessageStatus {

    /**
     * 待发布。
     */
    PENDING_PUBLISH,

    /**
     * 已发布。
     */
    PUBLISHED,

    /**
     * 处理中。
     */
    PROCESSING,

    /**
     * 重试中。
     */
    RETRYING,

    /**
     * 处理失败。
     */
    FAILED,

    /**
     * 处理完成。
     */
    COMPLETED
}
