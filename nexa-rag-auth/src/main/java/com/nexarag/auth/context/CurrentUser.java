package com.nexarag.auth.context;

/**
 * 表示当前请求中的用户身份。
 *
 * @param userId 用户 ID
 */
public record CurrentUser(String userId) {
}
