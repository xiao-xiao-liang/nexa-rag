package com.nexarag.auth.tenant.impl;

import com.nexarag.auth.context.UserContext;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.mapper.TenantMapper;
import com.nexarag.auth.mapper.TenantMemberMapper;
import com.nexarag.auth.model.dataobject.TenantMemberDO;
import com.nexarag.auth.tenant.TenantAccessGuard;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 依据启用租户与有效成员关系复验当前登录用户访问范围。
 */
@Service
@RequiredArgsConstructor
public class TenantAccessGuardImpl implements TenantAccessGuard {

    private final TenantMapper tenantMapper;
    private final TenantMemberMapper tenantMemberMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void requireCurrentUserAccess(String tenantId) {
        requireUserAccess(Long.valueOf(UserContext.getUserId()), tenantId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void requireUserAccess(Long userId, String tenantId) {
        // 1. 输入租户必须存在且启用
        if (tenantId == null || tenantId.isBlank() || tenantMapper.selectActiveByIdForUpdate(tenantId) == null) {
            throw ClientException.forbidden(AuthErrorCode.TENANT_MEMBERSHIP_UNAVAILABLE);
        }

        // 2. 当前登录用户必须持有该租户的有效成员资格
        TenantMemberDO member = tenantMemberMapper.selectActiveByTenantIdAndUserIdForUpdate(tenantId, userId);
        if (member == null) {
            throw ClientException.forbidden(AuthErrorCode.TENANT_MEMBERSHIP_UNAVAILABLE);
        }
    }
}
