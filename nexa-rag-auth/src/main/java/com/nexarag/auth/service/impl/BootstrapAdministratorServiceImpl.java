package com.nexarag.auth.service.impl;

import com.nexarag.auth.config.BootstrapAdministratorProperties;
import com.nexarag.auth.constants.TenantConstants;
import com.nexarag.auth.enums.GlobalRoleCode;
import com.nexarag.auth.enums.TenantMemberRole;
import com.nexarag.auth.enums.TenantMemberStatus;
import com.nexarag.auth.enums.UserStatus;
import com.nexarag.auth.mapper.AuthRoleMapper;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.mapper.EmailCredentialMapper;
import com.nexarag.auth.mapper.TenantMemberMapper;
import com.nexarag.auth.model.dataobject.AuthRoleDO;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.model.dataobject.EmailCredentialDO;
import com.nexarag.auth.model.dataobject.TenantMemberDO;
import com.nexarag.auth.service.AccountNamePolicy;
import com.nexarag.auth.service.BootstrapAdministratorService;
import com.nexarag.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 默认管理员初始化实现，预占管理员邮箱但不创建密码、验证码或登录态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BootstrapAdministratorServiceImpl implements BootstrapAdministratorService {

    /** 历史数据使用的不可变默认管理员用户ID。 */
    private static final long BOOTSTRAP_ADMINISTRATOR_USER_ID = 864019719617777664L;

    private final BootstrapAdministratorProperties properties;
    private final AccountNamePolicy accountNamePolicy;
    private final AuthUserMapper authUserMapper;
    private final AuthRoleMapper authRoleMapper;
    private final EmailCredentialMapper emailCredentialMapper;
    private final TenantMemberMapper tenantMemberMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void initializeIfEnabled() {
        if (!properties.isEnabled()) {
            return;
        }
        String accountName = requireText(properties.getAccountName(), "默认管理员账号名");
        String accountNameKey = accountNamePolicy.normalizeAndValidate(accountName);
        String emailKey = normalizeEmail(requireText(properties.getEmail(), "默认管理员预置邮箱"));
        try {
            initialize(accountName.trim(), accountNameKey, emailKey);
        } catch (DuplicateKeyException exception) {
            // 并发启动时由唯一约束裁决；若保留用户已由另一实例创建，则本次安全地视为成功。
            AuthUserDO user = authUserMapper.selectByUserIdForUpdate(BOOTSTRAP_ADMINISTRATOR_USER_ID);
            if (isExpectedBootstrapUser(user, accountNameKey)) {
                log.info("默认管理员已由并发实例初始化，userId={}", BOOTSTRAP_ADMINISTRATOR_USER_ID);
                return;
            }
            throw exception;
        }
    }

    /**
     * 执行保留管理员的幂等初始化写入。
     */
    private void initialize(String accountName, String accountNameKey, String emailKey) {
        AuthUserDO historicalUser = authUserMapper.selectByUserIdForUpdate(BOOTSTRAP_ADMINISTRATOR_USER_ID);
        if (historicalUser != null) {
            if (!isExpectedBootstrapUser(historicalUser, accountNameKey)) {
                throw new ServiceException("默认管理员历史用户与当前部署账号名不一致");
            }
            ensureBootstrapEmailCredential(emailKey);
            return;
        }
        AuthUserDO accountNameUser = authUserMapper.selectByAccountNameKeyForUpdate(accountNameKey);
        if (accountNameUser != null) {
            throw new ServiceException("默认管理员账号名已被其他用户占用");
        }
        EmailCredentialDO emailCredential = emailCredentialMapper.selectByEmailKeyForUpdate(emailKey);
        if (emailCredential != null) {
            throw new ServiceException("默认管理员预置邮箱已被其他用户占用");
        }
        AuthRoleDO administratorRole = authRoleMapper.selectByRoleCode(GlobalRoleCode.ADMIN.name());
        if (administratorRole == null) {
            throw new ServiceException("未找到 ADMIN 预置角色");
        }

        LocalDateTime now = LocalDateTime.now();
        authUserMapper.insert(new AuthUserDO(BOOTSTRAP_ADMINISTRATOR_USER_ID, accountName, null, accountNameKey,
                administratorRole.getRoleId(), UserStatus.ACTIVE.getCode(), TenantConstants.DEFAULT_TENANT_ID, now, now));
        // 配置邮箱由部署管理员控制，初始化时即作为管理员账号的唯一邮箱凭据预占，避免被普通注册抢占。
        emailCredentialMapper.insert(new EmailCredentialDO(BOOTSTRAP_ADMINISTRATOR_USER_ID,
                properties.getEmail().trim(), emailKey, now, now, now));
        tenantMemberMapper.insert(new TenantMemberDO(TenantConstants.DEFAULT_TENANT_ID,
                BOOTSTRAP_ADMINISTRATOR_USER_ID, TenantMemberRole.OWNER.getCode(),
                TenantMemberStatus.ACTIVE.getCode(), now, now));
        log.info("默认管理员初始化完成，userId={}", BOOTSTRAP_ADMINISTRATOR_USER_ID);
    }

    /**
     * 为早期已创建但未预占邮箱的默认管理员补齐唯一邮箱凭据。
     */
    private void ensureBootstrapEmailCredential(String emailKey) {
        EmailCredentialDO emailCredential = emailCredentialMapper.selectByEmailKeyForUpdate(emailKey);
        if (emailCredential != null) {
            if (emailCredential.getUserId() == null
                    || emailCredential.getUserId() != BOOTSTRAP_ADMINISTRATOR_USER_ID) {
                throw new ServiceException("默认管理员预置邮箱已被其他用户占用");
            }
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        emailCredentialMapper.insert(new EmailCredentialDO(BOOTSTRAP_ADMINISTRATOR_USER_ID,
                properties.getEmail().trim(), emailKey, now, now, now));
        log.info("默认管理员历史邮箱凭据已补齐，userId={}", BOOTSTRAP_ADMINISTRATOR_USER_ID);
    }

    /**
     * 判断已存在的历史用户是否与当前管理员配置一致。
     */
    private boolean isExpectedBootstrapUser(AuthUserDO user, String accountNameKey) {
        return user != null && accountNameKey.equals(user.getAccountNameKey());
    }

    /**
     * 校验部署配置中的必填文本。
     */
    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(fieldName + "不能为空");
        }
        return value;
    }

    /**
     * 规范化预置邮箱，仅用于检查其尚未被其他账号验证绑定。
     */
    private String normalizeEmail(String email) {
        String emailKey = email.trim().toLowerCase(Locale.ROOT);
        int atIndex = emailKey.lastIndexOf('@');
        if (emailKey.length() > 320 || atIndex <= 0 || atIndex == emailKey.length() - 1 || emailKey.indexOf(' ') >= 0) {
            throw new ServiceException("默认管理员预置邮箱格式不合法");
        }
        return emailKey;
    }
}
