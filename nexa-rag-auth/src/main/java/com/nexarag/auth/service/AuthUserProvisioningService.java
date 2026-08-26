package com.nexarag.auth.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexarag.auth.enums.GlobalRoleCode;
import com.nexarag.auth.enums.TenantMemberRole;
import com.nexarag.auth.enums.TenantMemberStatus;
import com.nexarag.auth.enums.UserStatus;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.mapper.AuthRoleMapper;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.mapper.TenantMemberMapper;
import com.nexarag.auth.model.dataobject.AuthRoleDO;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.model.dataobject.TenantMemberDO;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.nexarag.auth.constants.TenantConstants.DEFAULT_TENANT_ID;

/**
 * 创建普通用户及其默认租户成员关系的内部服务。
 *
 * <p>调用方必须已经处于事务内，并负责与自身凭据（邮箱或第三方身份）一起原子提交。</p>
 */
@Service
@RequiredArgsConstructor
public class AuthUserProvisioningService {

    /** 默认 USER 角色 ID 缓存；预置角色在部署期固定。 */
    private volatile Long userRoleId;

    private final AccountNamePolicy accountNamePolicy;
    private final AuthUserMapper authUserMapper;
    private final AuthRoleMapper authRoleMapper;
    private final TenantMemberMapper tenantMemberMapper;

    /**
     * 原子写入一个启用用户和默认租户成员关系。
     *
     * @param accountName 用户输入的账号名
     * @return 已创建用户
     */
    public AuthUserDO createDefaultTenantUser(String accountName) {
        // 1. 账号名规则和可用性先行校验，避免产生无凭据孤儿用户
        String accountNameKey = accountNamePolicy.normalizeAndValidate(accountName);
        if (authUserMapper.selectByAccountNameKey(accountNameKey) != null) {
            throw new ClientException(AuthErrorCode.ACCOUNT_NAME_CONFLICT);
        }

        // 2. 写入用户和默认租户成员关系；并发冲突由调用方的数据库唯一约束处理
        Long userId = IdWorker.getId();
        LocalDateTime now = LocalDateTime.now();
        AuthUserDO user = new AuthUserDO(userId, accountName.trim(), accountNameKey, getUserRoleId(),
                UserStatus.ACTIVE.getCode(), DEFAULT_TENANT_ID, now, now);
        authUserMapper.insert(user);
        tenantMemberMapper.insert(new TenantMemberDO(DEFAULT_TENANT_ID, userId,
                TenantMemberRole.MEMBER.getCode(), TenantMemberStatus.ACTIVE.getCode(), now, now));
        return user;
    }

    /**
     * 获取默认 USER 角色 ID，并在首次查询后缓存预置结果。
     */
    private Long getUserRoleId() {
        Long cachedRoleId = userRoleId;
        if (cachedRoleId != null) {
            return cachedRoleId;
        }
        synchronized (this) {
            if (userRoleId != null) {
                return userRoleId;
            }
            AuthRoleDO userRole = authRoleMapper.selectByRoleCode(GlobalRoleCode.USER.name());
            if (userRole == null) {
                throw new ServiceException("未找到 USER 预置角色");
            }
            userRoleId = userRole.getRoleId();
            return userRoleId;
        }
    }
}
