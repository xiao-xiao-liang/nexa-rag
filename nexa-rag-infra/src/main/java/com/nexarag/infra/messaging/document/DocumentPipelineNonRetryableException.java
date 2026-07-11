package com.nexarag.infra.messaging.document;

/**
 * 文档流水线不可重试异常，用于将永久性业务错误直接转入失败主题。
 */
public class DocumentPipelineNonRetryableException extends RuntimeException {

    /**
     * 使用错误信息和原始异常创建不可重试异常。
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public DocumentPipelineNonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 使用错误信息创建不可重试异常。
     *
     * @param message 错误信息
     */
    public DocumentPipelineNonRetryableException(String message) {
        super(message);
    }
}
