package com.nexarag.common.exception;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.error.IErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 客户端异常，用于表示请求参数、认证授权等用户侧错误。
 */
public class ClientException extends AbstractException {

    /** 可选的 HTTP 状态码；为空时沿用既有统一异常响应状态。 */
    @Getter
    private final HttpStatus httpStatus;

    public ClientException(String message) {
        this(message, null, BaseErrorCode.CLIENT_ERROR, null);
    }

    public ClientException(IErrorCode errorCode) {
        this(null, null, errorCode, null);
    }

    public ClientException(String message, IErrorCode errorCode) {
        this(message, null, errorCode, null);
    }

    public ClientException(String message, Throwable throwable, IErrorCode errorCode) {
        this(message, throwable, errorCode, null);
    }

    /**
     * 使用指定 HTTP 状态创建客户端异常。
     *
     * @param errorCode 业务错误码
     * @param httpStatus HTTP 状态码
     */
    public ClientException(IErrorCode errorCode, HttpStatus httpStatus) {
        this(null, null, errorCode, httpStatus);
    }

    /**
     * 创建统一未认证客户端异常。
     *
     * @param errorCode 业务错误码
     * @return HTTP 401 客户端异常
     */
    public static ClientException unauthorized(IErrorCode errorCode) {
        return new ClientException(errorCode, HttpStatus.UNAUTHORIZED);
    }

    /**
     * 创建统一无权限客户端异常。
     *
     * @param errorCode 业务错误码
     * @return HTTP 403 客户端异常
     */
    public static ClientException forbidden(IErrorCode errorCode) {
        return new ClientException(errorCode, HttpStatus.FORBIDDEN);
    }

    private ClientException(String message, Throwable throwable, IErrorCode errorCode, HttpStatus httpStatus) {
        super(message, throwable, errorCode);
        this.httpStatus = httpStatus;
    }
}
