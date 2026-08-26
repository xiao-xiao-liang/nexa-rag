package com.nexarag.auth.tenant.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.nexarag.auth.constants.AuthSessionConstants;
import com.nexarag.auth.constants.TenantConstants;
import com.nexarag.auth.context.UserContext;
import com.nexarag.auth.tenant.CurrentTenantService;
import com.nexarag.auth.tenant.TenantAccessGuard;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 当前工作空间服务，确保客户端不能以参数或 Header 覆盖 Token-Session 租户。
 */
@Service
@RequiredArgsConstructor
public class CurrentTenantServiceImpl implements CurrentTenantService {

    private final TenantAccessGuard tenantAccessGuard;

    /** {@inheritDoc} */
    @Override
    public String getRequiredCurrentTenantId() {
        String tenantId = UserContext.getTenantId();
        try {
            tenantAccessGuard.requireCurrentUserAccess(tenantId);
            return tenantId;
        } catch (ClientException exception) {
            // 当前工作空间失效时仅回退当前设备登录态，不撤销该账号在其他工作空间的资格。
            switchToDefaultTenant();
            return TenantConstants.DEFAULT_TENANT_ID;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void switchCurrentTenant(String tenantId) {
        tenantAccessGuard.requireCurrentUserAccess(tenantId);
        StpUtil.getTokenSession().set(AuthSessionConstants.CURRENT_TENANT_ID, tenantId);
    }

    /**
     * 回退默认租户；默认成员关系异常时仍拒绝当前请求。
     */
    private void switchToDefaultTenant() {
        tenantAccessGuard.requireCurrentUserAccess(TenantConstants.DEFAULT_TENANT_ID);
        StpUtil.getTokenSession().set(AuthSessionConstants.CURRENT_TENANT_ID, TenantConstants.DEFAULT_TENANT_ID);
    }
}
