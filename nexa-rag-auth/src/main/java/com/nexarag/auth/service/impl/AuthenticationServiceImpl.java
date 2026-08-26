package com.nexarag.auth.service.impl;

import com.nexarag.auth.enums.TenantMemberStatus;
import com.nexarag.auth.enums.UserStatus;
import com.nexarag.auth.enums.EmailVerificationPurpose;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.mapper.EmailCredentialMapper;
import com.nexarag.auth.mapper.PasswordCredentialMapper;
import com.nexarag.auth.mapper.TenantMemberMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.model.dataobject.EmailCredentialDO;
import com.nexarag.auth.model.dataobject.PasswordCredentialDO;
import com.nexarag.auth.model.dataobject.TenantMemberDO;
import com.nexarag.auth.model.dto.AccountPasswordLoginDTO;
import com.nexarag.auth.model.dto.EmailCodeLoginDTO;
import com.nexarag.auth.model.dto.EmailCodeSendDTO;
import com.nexarag.auth.model.dto.EmailPasswordLoginDTO;
import com.nexarag.auth.model.vo.EmailChallengeVO;
import com.nexarag.auth.model.vo.LoginSessionVO;
import com.nexarag.auth.service.AuthenticationService;
import com.nexarag.auth.service.CurrentUserProfileService;
import com.nexarag.auth.service.EmailChallengeService;
import com.nexarag.auth.service.PasswordService;
import com.nexarag.auth.service.SecurityAuditService;
import com.nexarag.auth.service.SessionService;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 本地账号密码认证实现，负责失败计数、冻结校验和 Sa-Token 登录态建立。
 */
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthUserMapper authUserMapper;
    private final EmailCredentialMapper emailCredentialMapper;
    private final PasswordCredentialMapper passwordCredentialMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final EmailChallengeService emailChallengeService;
    private final PasswordService passwordService;
    private final SessionService sessionService;
    private final PasswordLoginTransactionService passwordLoginTransactionService;
    private final CurrentUserProfileService currentUserProfileService;
    private final SecurityAuditService securityAuditService;

    /**
     * {@inheritDoc}
     */
    @Override
    public LoginSessionVO loginByAccountPassword(AccountPasswordLoginDTO loginDTO) {
        // 1. 规范化账号名并读取用户，最终状态由短事务再次确认
        if (loginDTO == null) {
            throw authenticationFailed();
        }
        AuthUserDO user = authUserMapper.selectByAccountNameKey(normalizeAccountName(loginDTO.getAccountName()));
        return loginByUserPassword(user, null, loginDTO.getPassword());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LoginSessionVO loginByEmailPassword(EmailPasswordLoginDTO loginDTO) {
        // 1. 读取当前邮箱凭据，最终归属由短事务再次确认
        if (loginDTO == null) {
            throw authenticationFailed();
        }
        String emailKey = normalizeEmail(loginDTO.getEmail());
        EmailCredentialDO emailCredential = emailCredentialMapper.selectByEmailKey(emailKey);
        AuthUserDO user = emailCredential == null ? null : authUserMapper.selectById(emailCredential.getUserId());
        return loginByUserPassword(user, emailKey, loginDTO.getPassword());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EmailChallengeVO sendAnonymousEmailCode(EmailCodeSendDTO sendDTO) {
        // 1. 匿名入口只允许注册、邮箱登录和密码重置三种明确用途
        if (sendDTO == null || sendDTO.getPurpose() == null) {
            throw authenticationFailed();
        }

        // 2. 根据用途执行相应的身份绑定和频率限制逻辑
        return switch (sendDTO.getPurpose()) {
            case REGISTER -> emailChallengeService.sendCode(sendDTO.getEmail(), EmailVerificationPurpose.REGISTER, null);
            case EMAIL_LOGIN -> sendEmailLoginCodeInternal(sendDTO.getEmail());
            case PASSWORD_RESET -> sendPasswordResetCodeInternal(sendDTO.getEmail());
            default -> throw authenticationFailed();
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(noRollbackFor = ClientException.class)
    public LoginSessionVO loginByEmailCode(EmailCodeLoginDTO loginDTO) {
        // 1. 重新锁定当前邮箱凭据，确保验证码不能在邮箱换绑后继续使用
        if (loginDTO == null) {
            throw authenticationFailed();
        }
        EmailCredentialDO emailCredential = emailCredentialMapper.selectByEmailKeyForUpdate(normalizeEmail(loginDTO.getEmail()));
        AuthUserDO user = emailCredential == null ? null : authUserMapper.selectByUserIdForUpdate(emailCredential.getUserId());
        if (!isActiveUser(user)) {
            throw authenticationFailed();
        }

        // 2. 消费绑定当前用户和邮箱用途的验证码后建立登录态
        emailChallengeService.verifyAndConsume(loginDTO.getChallengeId(), loginDTO.getEmail(), EmailVerificationPurpose.EMAIL_LOGIN,
                user.getUserId(), loginDTO.getVerificationCode());
        return establishLogin(user, true);
    }

    /**
     * 执行邮箱验证码登录挑战创建，最终邮箱归属由后续验证码消费事务确认。
     */
    private EmailChallengeVO sendEmailLoginCodeInternal(String email) {
        String emailKey = normalizeEmail(email);
        EmailCredentialDO emailCredential = emailCredentialMapper.selectByEmailKey(emailKey);
        AuthUserDO user = emailCredential == null ? null : authUserMapper.selectById(emailCredential.getUserId());
        if (!isActiveUser(user)) {
            throw authenticationFailed();
        }
        return emailChallengeService.sendCode(email.trim(), EmailVerificationPurpose.EMAIL_LOGIN, user.getUserId());
    }

    /**
     * 执行密码重置挑战创建，最终邮箱归属由后续验证码消费事务确认。
     */
    private EmailChallengeVO sendPasswordResetCodeInternal(String email) {
        String emailKey = normalizeEmail(email);
        EmailCredentialDO emailCredential = emailCredentialMapper.selectByEmailKey(emailKey);
        AuthUserDO user = emailCredential == null ? null : authUserMapper.selectById(emailCredential.getUserId());
        if (!isActiveUser(user)) {
            throw authenticationFailed();
        }
        return emailChallengeService.sendCode(email.trim(), EmailVerificationPurpose.PASSWORD_RESET, user.getUserId());
    }

    /**
     * 验证本地密码并建立登录态。
     */
    private LoginSessionVO loginByUserPassword(AuthUserDO user, String emailKey, String rawPassword) {
        if (!isActiveUser(user)) {
            throw authenticationFailed();
        }

        // 1. 在无事务、无行锁范围完成 Argon2 密码校验和可选哈希升级
        for (int attempt = 0; attempt < 2; attempt++) {
            PasswordCredentialDO credential = passwordCredentialMapper.selectById(user.getUserId());
            if (credential == null || isPasswordLocked(credential, LocalDateTime.now())) {
                throw authenticationFailed();
            }
            String verifiedPasswordHash = credential.getPasswordHash();
            boolean passwordMatched = passwordService.matches(rawPassword, verifiedPasswordHash);
            String upgradedPasswordHash = passwordMatched && passwordService.shouldUpgrade(verifiedPasswordHash)
                    ? passwordService.rehashVerified(rawPassword) : null;

            // 2. 短事务内重新锁定并确认哈希未变化，避免密码重置竞态绕过
            PasswordLoginTransactionService.PasswordLoginCompletion completion = passwordLoginTransactionService.complete(
                    user.getUserId(), emailKey, verifiedPasswordHash, passwordMatched, upgradedPasswordHash);
            if (completion.shouldRetry()) {
                continue;
            }
            if (completion.session() != null) {
                return completion.session();
            }
            throw authenticationFailed();
        }
        throw authenticationFailed();
    }

    /**
     * 校验默认租户成员关系后注册提交后登录态建立动作。
     */
    private LoginSessionVO establishLogin(AuthUserDO user, boolean grantRecentVerification) {
        TenantMemberDO member = tenantMemberMapper.selectActiveByTenantIdAndUserIdForUpdate(
                user.getDefaultTenantId(), user.getUserId());
        if (member == null || member.getMemberStatus() != TenantMemberStatus.ACTIVE.getCode()) {
            throw authenticationFailed();
        }
        sessionService.establishLoginAfterCommit(user.getUserId(), user.getDefaultTenantId(), grantRecentVerification);
        securityAuditService.recordSuccess(user.getUserId(), "EMAIL_CODE_LOGIN", "已通过邮箱验证码登录");
        return currentUserProfileService.getProfile(user.getUserId(), user.getDefaultTenantId());
    }

    /**
     * 规范化登录账号名；非法或空输入也会进入统一登录失败分支。
     *
     * @param accountName 用户输入的账号名
     * @return 小写规范化账号名
     */
    private String normalizeAccountName(String accountName) {
        return accountName == null ? "" : accountName.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 规范化邮箱登录键；格式异常同样会进入统一认证失败分支。
     */
    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 创建不暴露账号、凭据或状态差异的统一未认证异常。
     *
     * @return HTTP 401 客户端异常
     */
    private ClientException authenticationFailed() {
        return ClientException.unauthorized(AuthErrorCode.AUTHENTICATION_FAILED);
    }

    /**
     * 判断用户是否可进行本地登录。
     *
     * @param user 已锁定用户
     * @return 用户存在且处于启用状态时返回 true
     */
    private boolean isActiveUser(AuthUserDO user) {
        return user != null && user.getStatus() != null && user.getStatus() == UserStatus.ACTIVE.getCode()
                && user.getDefaultTenantId() != null && !user.getDefaultTenantId().isBlank();
    }

    /**
     * 判断密码凭据是否仍在冻结期。
     *
     * @param credential 密码凭据
     * @param now 当前时间
     * @return 仍被冻结时返回 true
     */
    private boolean isPasswordLocked(PasswordCredentialDO credential, LocalDateTime now) {
        return credential.getPasswordLockedUntil() != null && credential.getPasswordLockedUntil().isAfter(now);
    }

}
