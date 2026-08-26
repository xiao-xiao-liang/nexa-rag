package com.nexarag.auth.service;

/**
 * Sa-Token 业务会话服务，确保会话仅在数据库事务提交后建立。
 */
public interface SessionService {

    /**
     * 在当前事务成功提交后建立登录态并写入设备级当前租户。
     *
     * @param userId 稳定用户ID
     * @param tenantId 当前租户ID
     */
    void establishLoginAfterCommit(Long userId, String tenantId);

    /**
     * 在事务成功提交后建立登录态，并按认证强度决定是否签发最近验证授权。
     *
     * @param userId 稳定用户ID
     * @param tenantId 当前租户ID
     * @param grantRecentVerification 是否签发当前新会话的最近验证授权
     */
    void establishLoginAfterCommit(Long userId, String tenantId, boolean grantRecentVerification);

    /**
     * 在当前事务成功提交后撤销指定用户的全部登录态。
     *
     * @param userId 稳定用户ID
     */
    void revokeAllSessionsAfterCommit(Long userId);
}
