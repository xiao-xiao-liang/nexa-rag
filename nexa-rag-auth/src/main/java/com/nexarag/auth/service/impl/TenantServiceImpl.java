package com.nexarag.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexarag.auth.context.UserContext;
import com.nexarag.auth.enums.TenantMemberRole;
import com.nexarag.auth.enums.TenantMemberStatus;
import com.nexarag.auth.enums.TenantStatus;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.mapper.TenantMapper;
import com.nexarag.auth.mapper.TenantMemberMapper;
import com.nexarag.auth.model.dataobject.TenantDO;
import com.nexarag.auth.model.dataobject.TenantMemberDO;
import com.nexarag.auth.model.dto.CreateTenantDTO;
import com.nexarag.auth.model.vo.TenantVO;
import com.nexarag.auth.service.TenantService;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/** 企业租户创建实现，仅允许全局管理员创建新的企业工作空间。 */
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final TenantMapper tenantMapper;
    private final TenantMemberMapper tenantMemberMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TenantVO createEnterpriseTenant(CreateTenantDTO createDTO) {
        // 1. 校验全局管理员身份与租户名称
        if (!StpUtil.hasRole("ADMIN")) {
            throw ClientException.forbidden(AuthErrorCode.ACCESS_DENIED);
        }
        if (createDTO == null || createDTO.tenantName() == null || createDTO.tenantName().isBlank()) {
            throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        }
        String tenantName = createDTO.tenantName().trim();
        String tenantNameKey = tenantName.toLowerCase(Locale.ROOT);
        if (tenantMapper.selectByTenantNameKey(tenantNameKey) != null) {
            throw new ClientException(AuthErrorCode.TENANT_NAME_CONFLICT);
        }

        // 2. 原子创建租户及唯一初始所有者成员关系
        Long userId = Long.valueOf(UserContext.getUserId());
        String tenantId = String.valueOf(IdWorker.getId());
        LocalDateTime now = LocalDateTime.now();
        try {
            tenantMapper.insert(new TenantDO(tenantId, tenantName, tenantNameKey, 1, TenantStatus.ACTIVE.getCode(),
                    userId, now, now));
            tenantMemberMapper.insert(new TenantMemberDO(tenantId, userId, TenantMemberRole.OWNER.getCode(),
                    TenantMemberStatus.ACTIVE.getCode(), now, now));
        } catch (DuplicateKeyException exception) {
            throw new ClientException(AuthErrorCode.TENANT_NAME_CONFLICT);
        }
        return new TenantVO(tenantId, tenantName);
    }
}
