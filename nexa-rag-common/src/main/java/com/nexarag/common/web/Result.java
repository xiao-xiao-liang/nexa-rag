package com.nexarag.common.web;

import lombok.Builder;

/**
 * 统一接口响应结果。
 *
 * @param code    响应编码
 * @param message 响应消息
 * @param data    响应数据
 * @param traceId 链路追踪 ID
 */
@Builder
public record Result<T>(String code, String message, T data, String traceId) {

    /**
     * 成功状态码。
     */
    public static final String SUCCESS_CODE = "0";

    /**
     * 判断请求是否成功。
     *
     * @return 如果响应编码为成功状态码，返回 true；否则返回 false
     */
    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }
}
