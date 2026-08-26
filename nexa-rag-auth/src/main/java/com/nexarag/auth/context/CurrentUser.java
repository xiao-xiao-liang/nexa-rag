package com.nexarag.auth.context;

/**
 * 表示当前请求中的用户身份。
 *
 * @param userId 稳定用户ID
 * @param tenantId 当前租户ID
 */
public record CurrentUser(String userId, String tenantId) {
}
