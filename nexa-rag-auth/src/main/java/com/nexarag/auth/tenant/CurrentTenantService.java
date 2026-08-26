package com.nexarag.auth.tenant;

/**
 * 管理仅绑定当前 Sa-Token Token-Session 的工作空间选择。
 */
public interface CurrentTenantService {

    /** 获取并复验当前工作空间；失效时自动回退默认租户。 */
    String getRequiredCurrentTenantId();

    /** 将当前设备登录态切换至有有效成员资格的租户。 */
    void switchCurrentTenant(String tenantId);
}
