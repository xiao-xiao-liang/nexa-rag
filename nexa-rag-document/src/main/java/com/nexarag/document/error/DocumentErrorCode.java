package com.nexarag.document.error;

import com.nexarag.common.error.IErrorCode;
import lombok.AllArgsConstructor;

/**
 * 文档模块错误码。
 */
@AllArgsConstructor
public enum DocumentErrorCode implements IErrorCode {

    /**
     * 文档不存在。
     */
    DOCUMENT_NOT_FOUND("D000001", "文档不存在"),

    /**
     * 文档状态不允许执行当前操作。
     */
    DOCUMENT_STATUS_INVALID("D000002", "文档状态不允许执行当前操作"),

    /**
     * 不支持的文档类型。
     */
    DOCUMENT_FILE_TYPE_UNSUPPORTED("D000003", "不支持的文档类型"),

    /**
     * 文档处理配置不合法。
     */
    DOCUMENT_PROCESS_CONFIG_INVALID("D000004", "文档处理配置不合法");

    private final String code;
    private final String message;

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
