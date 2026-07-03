package com.nexarag.common.exception;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.error.IErrorCode;

/**
 * 服务端异常，用于表示系统执行错误、业务处理失败等服务侧错误。
 */
public class ServiceException extends AbstractException {

    public ServiceException(String message) {
        this(message, null, BaseErrorCode.SERVICE_ERROR);
    }

    public ServiceException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    public ServiceException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public ServiceException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }
}
