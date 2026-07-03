package com.nexarag.common.exception;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.error.IErrorCode;

/**
 * 客户端异常，用于表示请求参数、认证授权等用户侧错误。
 */
public class ClientException extends AbstractException {

    public ClientException(String message) {
        this(message, null, BaseErrorCode.CLIENT_ERROR);
    }

    public ClientException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    public ClientException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public ClientException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }
}
