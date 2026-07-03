package com.nexarag.common.web;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.AbstractException;
import com.nexarag.common.trace.TraceIdContext;

/**
 * 统一响应结果构造工具。
 */
public final class Results {

    private Results() {
    }

    /**
     * 构造无数据成功响应。
     *
     * @return 成功响应
     */
    public static Result<Void> success() {
        return Result.<Void>builder()
                .code(Result.SUCCESS_CODE)
                .traceId(TraceIdContext.getTraceId())
                .build();
    }

    /**
     * 构造成功响应。
     *
     * @param data 响应数据
     * @param <T>  响应数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(Result.SUCCESS_CODE)
                .data(data)
                .traceId(TraceIdContext.getTraceId())
                .build();
    }

    /**
     * 构造默认失败响应。
     *
     * @return 失败响应
     */
    public static Result<Void> failure() {
        return failure(BaseErrorCode.SERVICE_ERROR.code(), BaseErrorCode.SERVICE_ERROR.message());
    }

    /**
     * 通过抽象异常构造失败响应。
     *
     * @param exception 抽象异常
     * @return 失败响应
     */
    public static Result<Void> failure(AbstractException exception) {
        return failure(exception.getErrorCode(), exception.getErrorMessage());
    }

    /**
     * 构造指定错误码和错误消息的失败响应。
     *
     * @param errorCode    错误码
     * @param errorMessage 错误消息
     * @return 失败响应
     */
    public static Result<Void> failure(String errorCode, String errorMessage) {
        return Result.<Void>builder()
                .code(errorCode)
                .message(errorMessage)
                .traceId(TraceIdContext.getTraceId())
                .build();
    }
}
