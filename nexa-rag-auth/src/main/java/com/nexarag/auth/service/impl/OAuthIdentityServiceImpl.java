package com.nexarag.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexarag.auth.enums.OAuthProvider;
import com.nexarag.auth.enums.TenantMemberStatus;
import com.nexarag.auth.enums.UserStatus;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.mapper.EmailCredentialMapper;
import com.nexarag.auth.mapper.ExternalIdentityMapper;
import com.nexarag.auth.mapper.PasswordCredentialMapper;
import com.nexarag.auth.mapper.TenantMemberMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.model.dataobject.ExternalIdentityDO;
import com.nexarag.auth.model.dataobject.TenantMemberDO;
import com.nexarag.auth.model.vo.LoginSessionVO;
import com.nexarag.auth.model.vo.ExternalIdentityVO;
import com.nexarag.auth.model.vo.OAuthCallbackVO;
import com.nexarag.auth.service.AuthUserProvisioningService;
import com.nexarag.auth.service.CurrentUserProfileService;
import com.nexarag.auth.service.OAuthAccountNameGenerator;
import com.nexarag.auth.service.OAuthIdentityService;
import com.nexarag.auth.service.SecurityAuditService;
import com.nexarag.auth.service.SessionService;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OAuth 稳定主体绑定实现，所有身份归属变更均在数据库事务内完成。
 */
@Service
@RequiredArgsConstructor
public class OAuthIdentityServiceImpl implements OAuthIdentityService {

    private final ExternalIdentityMapper externalIdentityMapper;
    private final AuthUserMapper authUserMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final EmailCredentialMapper emailCredentialMapper;
    private final PasswordCredentialMapper passwordCredentialMapper;
    private final AuthUserProvisioningService authUserProvisioningService;
    private final OAuthAccountNameGenerator oauthAccountNameGenerator;
    private final SessionService sessionService;
    private final SecurityAuditService securityAuditService;
    private final CurrentUserProfileService currentUserProfileService;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(noRollbackFor = ClientException.class)
    public OAuthCallbackVO loginOrRegister(OAuthProvider provider, String providerSubject, String displayName,
                                           String accountName) {
        // 1. 锁定稳定第三方主体，避免同一第三方账号被并发注册到多个本地用户
        ExternalIdentityDO identity = externalIdentityMapper.selectByProviderAndSubjectForUpdate(
                provider.getCode(), providerSubject);
        AuthUserDO user;
        if (identity == null) {
            // 2. 未绑定主体自动创建本地用户；稳定主体只参与账号名哈希，不直接落入可见字段
            user = registerExternalIdentity(provider, providerSubject, displayName, accountName);
        } else {
            user = requireActiveUserWithDefaultTenant(identity.getUserId());
        }

        // 3. 第三方授权已验证身份，提交后建立独立 Sa-Token 登录态并签发最近验证授权
        sessionService.establishLoginAfterCommit(user.getUserId(), user.getDefaultTenantId(), true);
        securityAuditService.recordSuccess(user.getUserId(), "OAUTH_LOGIN", "已通过 " + provider.getCode() + " 第三方账号登录");
        return callback("LOGIN", user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(noRollbackFor = ClientException.class)
    public OAuthCallbackVO bind(Long userId, OAuthProvider provider, String providerSubject) {
        // 1. 锁定当前用户和默认租户成员关系，确保已禁用用户不能通过迟到回调写入新凭据
        AuthUserDO user = requireActiveUserWithDefaultTenant(userId);
        ExternalIdentityDO identity = externalIdentityMapper.selectByProviderAndSubjectForUpdate(
                provider.getCode(), providerSubject);
        if (identity != null && !identity.getUserId().equals(userId)) {
            throw new ClientException(AuthErrorCode.EXTERNAL_IDENTITY_CONFLICT);
        }

        // 2. 相同主体重复回调保持幂等；仅在首次绑定时落库并记录安全审计
        if (identity == null) {
            try {
                LocalDateTime now = LocalDateTime.now();
                externalIdentityMapper.insert(new ExternalIdentityDO(IdWorker.getId(), userId, provider.getCode(),
                        providerSubject, now, now, now));
            } catch (DuplicateKeyException exception) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                throw new ClientException(AuthErrorCode.EXTERNAL_IDENTITY_CONFLICT);
            }
            securityAuditService.recordSuccess(userId, "OAUTH_BIND", "已绑定 " + provider.getCode() + " 第三方账号");
        }
        return callback("BIND", user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ExternalIdentityVO> listByUserId(Long userId) {
        return externalIdentityMapper.selectByUserId(userId).stream()
                .map(identity -> new ExternalIdentityVO(identity.getExternalIdentityId(), identity.getProviderCode(),
                        identity.getBindTime()))
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(noRollbackFor = ClientException.class)
    public void unbind(Long userId, OAuthProvider provider, Long externalIdentityId) {
        if (externalIdentityId == null) {
            return;
        }
        // 1. 同时锁定所有登录凭据，保证“最后一种凭据”判断和删除动作不存在并发窗口
        requireActiveUserWithDefaultTenant(userId);
        List<ExternalIdentityDO> identities = externalIdentityMapper.selectByUserIdForUpdate(userId);
        boolean exists = identities.stream().anyMatch(identity -> externalIdentityId.equals(identity.getExternalIdentityId())
                && provider.getCode().equals(identity.getProviderCode()));
        if (!exists) {
            return;
        }
        boolean hasEmailCredential = emailCredentialMapper.selectByUserIdForUpdate(userId) != null;
        boolean hasPasswordCredential = passwordCredentialMapper.selectByUserIdForUpdate(userId) != null;
        if (!hasEmailCredential && !hasPasswordCredential && identities.size() == 1) {
            throw new ClientException(AuthErrorCode.LAST_LOGIN_CREDENTIAL_PROTECTED);
        }

        // 2. 删除目标身份并记录审计；第三方 access token 从未持久化，无需再调用平台撤销接口
        externalIdentityMapper.deleteByIdAndUserIdAndProviderCode(externalIdentityId, userId, provider.getCode());
        securityAuditService.recordSuccess(userId, "OAUTH_UNBIND", "已解绑 " + provider.getCode() + " 第三方账号");
    }

    /**
     * 创建用户与其第一条第三方身份绑定。
     */
    private AuthUserDO registerExternalIdentity(OAuthProvider provider, String providerSubject, String displayName,
                                                String accountName) {
        try {
            String generatedAccountName = oauthAccountNameGenerator.generate(provider, providerSubject, displayName,
                    accountName);
            AuthUserDO user = authUserProvisioningService.createDefaultTenantUser(generatedAccountName, displayName);
            LocalDateTime now = LocalDateTime.now();
            externalIdentityMapper.insert(new ExternalIdentityDO(IdWorker.getId(), user.getUserId(), provider.getCode(),
                    providerSubject, now, now, now));
            return user;
        } catch (DuplicateKeyException exception) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw new ClientException(AuthErrorCode.REGISTRATION_CONFLICT);
        }
    }

    /**
     * 锁定并验证用户状态与默认租户成员关系。
     */
    private AuthUserDO requireActiveUserWithDefaultTenant(Long userId) {
        AuthUserDO user = authUserMapper.selectByUserIdForUpdate(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != UserStatus.ACTIVE.getCode()
                || user.getDefaultTenantId() == null || user.getDefaultTenantId().isBlank()) {
            throw ClientException.unauthorized(AuthErrorCode.AUTHENTICATION_FAILED);
        }
        TenantMemberDO member = tenantMemberMapper.selectActiveByTenantIdAndUserIdForUpdate(
                user.getDefaultTenantId(), userId);
        if (member == null || member.getMemberStatus() != TenantMemberStatus.ACTIVE.getCode()) {
            throw ClientException.unauthorized(AuthErrorCode.AUTHENTICATION_FAILED);
        }
        return user;
    }

    /**
     * 将已验证用户转换为回调展示对象。
     */
    private OAuthCallbackVO callback(String action, AuthUserDO user) {
        return new OAuthCallbackVO(action, currentUserProfileService.getProfile(user.getUserId(), user.getDefaultTenantId()));
    }
}
