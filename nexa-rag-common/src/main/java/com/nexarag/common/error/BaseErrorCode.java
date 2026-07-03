package com.nexarag.common.error;

import lombok.AllArgsConstructor;

/**
 * 基础错误码枚举，遵循 A/B/C 三类错误码规范。
 */
@AllArgsConstructor
public enum BaseErrorCode implements IErrorCode {

    CLIENT_ERROR("A000001", "用户端错误"),
    PARAM_ERROR("A000002", "请求参数错误"),
    SERVICE_ERROR("B000001", "系统执行出错"),
    SERVICE_TIMEOUT_ERROR("B000100", "系统执行超时"),
    REMOTE_ERROR("C000001", "调用第三方服务出错");

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
