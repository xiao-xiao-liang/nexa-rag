package com.nexarag.common.exception;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.error.IErrorCode;

/**
 * 远程调用异常，用于表示模型服务、对象存储、消息队列等外部服务调用错误。
 */
public class RemoteException extends AbstractException {

    public RemoteException(String message) {
        this(message, null, BaseErrorCode.REMOTE_ERROR);
    }

    public RemoteException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    public RemoteException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public RemoteException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }
}
