package com.nexarag.document.tenant;

import org.springframework.stereotype.Component;

/**
 * 固定单租户阶段的当前租户提供者。
 */
@Component
public class FixedCurrentTenantProvider implements CurrentTenantProvider {

    /**
     * 返回内建默认租户，后续由认证模块提供同接口实现替换。
     *
     * @return 默认租户标识
     */
    @Override
    public String getRequiredTenantId() {
        return TenantConstants.DEFAULT_TENANT_ID;
    }
}
