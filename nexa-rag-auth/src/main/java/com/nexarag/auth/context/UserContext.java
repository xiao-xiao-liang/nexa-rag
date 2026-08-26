package com.nexarag.auth.context;

import cn.dev33.satoken.stp.StpUtil;
import com.nexarag.auth.constants.AuthSessionConstants;

import java.util.Optional;

/**
 * 基于 Sa-Token 登录态和 Token-Session 读取当前用户业务上下文的兼容门面。
 */
public final class UserContext {

    private UserContext() {
    }

    /**
     * 获取当前已登录用户及其当前租户。
     *
     * @return 当前用户业务上下文
     * @throws IllegalStateException 当前 Token 尚未建立当前租户时抛出
     */
    public static CurrentUser getCurrUser() {
        // 1. 从 Sa-Token 获取稳定登录用户ID，未登录时由框架抛出异常
        String userId = StpUtil.getLoginIdAsString();

        // 2. 从当前 Token-Session 获取设备级当前租户，不使用线程本地状态
        String tenantId = StpUtil.getTokenSession().getString(AuthSessionConstants.CURRENT_TENANT_ID);
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("当前登录态未设置租户");
        }

        // 3. 组装不可变业务上下文供既有模块使用
        return new CurrentUser(userId, tenantId);
    }

    public static String getUserId() {
        return StpUtil.getLoginIdAsString();
    }

    public static String getTenantId() {
        return Optional.ofNullable(StpUtil.getTokenSession().getString(AuthSessionConstants.CURRENT_TENANT_ID))
                .orElseThrow(() -> new IllegalStateException("当前登录态未设置租户"));
    }
}
