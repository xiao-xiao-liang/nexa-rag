package com.nexarag.auth.model.vo;

/**
 * 前端状态变更请求需要回传的 CSRF 挑战。
 *
 * @param token CSRF 挑战值，仅用于同源自定义请求头
 */
public record CsrfTokenVO(String token) {
}
