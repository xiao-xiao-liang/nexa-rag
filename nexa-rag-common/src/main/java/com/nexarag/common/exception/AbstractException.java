package com.nexarag.common.exception;

import com.nexarag.common.error.IErrorCode;
import lombok.Getter;
import org.springframework.util.StringUtils;

/**
 * 抽象业务异常基类，用于统一客户端、服务端和远程调用三类异常。
 */
@Getter
public abstract class AbstractException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;

    /**
     * 创建抽象异常。
     *
     * @param message   自定义错误消息
     * @param throwable 原始异常
     * @param errorCode 错误码
     */
    protected AbstractException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable);
        this.errorCode = errorCode.code();
        this.errorMessage = StringUtils.hasText(message) ? message : errorCode.message();
    }
}
