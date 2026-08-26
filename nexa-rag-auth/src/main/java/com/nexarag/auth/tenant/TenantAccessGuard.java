package com.nexarag.auth.tenant;

/**
 * 复验当前登录用户对租户的有效成员资格，供 Web、异步和流式边界统一调用。
 */
public interface TenantAccessGuard {

    /**
     * 确认当前登录用户仍可访问指定租户。
     *
     * @param tenantId 租户ID
     */
    void requireCurrentUserAccess(String tenantId);

    /**
     * 复验指定用户在异步或流式边界仍可访问指定租户。
     *
     * @param userId 稳定用户ID
     * @param tenantId 租户ID
     */
    void requireUserAccess(Long userId, String tenantId);
}
