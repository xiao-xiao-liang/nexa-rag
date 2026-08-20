package com.nexarag.document.tenant;

/**
 * 提供当前请求所属租户，为后续认证模块替换租户解析方式保留边界。
 */
public interface CurrentTenantProvider {

    /**
     * 获取当前请求的租户标识。
     *
     * @return 当前租户标识
     */
    String getRequiredTenantId();
}
