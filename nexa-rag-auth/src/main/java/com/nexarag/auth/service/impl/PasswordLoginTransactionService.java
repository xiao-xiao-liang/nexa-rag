package com.nexarag.auth.service.impl;

import com.nexarag.auth.enums.TenantMemberStatus;
import com.nexarag.auth.enums.UserStatus;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.mapper.EmailCredentialMapper;
import com.nexarag.auth.mapper.PasswordCredentialMapper;
import com.nexarag.auth.mapper.TenantMemberMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.model.dataobject.EmailCredentialDO;
import com.nexarag.auth.model.dataobject.PasswordCredentialDO;
import com.nexarag.auth.model.dataobject.TenantMemberDO;
import com.nexarag.auth.model.vo.LoginSessionVO;
import com.nexarag.auth.service.SessionService;
import com.nexarag.auth.service.CurrentUserProfileService;
import com.nexarag.auth.service.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 密码登录短事务服务，在事务外完成 Argon2 校验后仅锁定并确认状态变化。
 */
@Service
@RequiredArgsConstructor
public class PasswordLoginTransactionService {

    /** 密码连续失败达到该值时冻结账号密码验证。 */
    private static final int MAX_CONSECUTIVE_PASSWORD_FAILURES = 5;

    /** 密码验证冻结时长。 */
    private static final int PASSWORD_LOCK_MINUTES = 15;

    private final AuthUserMapper authUserMapper;
    private final EmailCredentialMapper emailCredentialMapper;
    private final PasswordCredentialMapper passwordCredentialMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final SessionService sessionService;
    private final CurrentUserProfileService currentUserProfileService;
    private final SecurityAuditService securityAuditService;

    /**
     * 锁定并最终确认一次事务外密码验证结果。
     *
     * @param userId 用户 ID
     * @param emailKey 邮箱登录时的规范化邮箱键；账号登录传 null
     * @param verifiedPasswordHash 事务外完成 Argon2 校验时读取的哈希
     * @param passwordMatched 事务外密码是否匹配
     * @param upgradedPasswordHash 需要升级时已在事务外生成的新哈希；否则为空
     * @return 完成结果；凭据发生并发变化时要求调用方重新读取并验证
     */
    @Transactional
    public PasswordLoginCompletion complete(Long userId, String emailKey, String verifiedPasswordHash,
                                            boolean passwordMatched, String upgradedPasswordHash) {
        // 1. 锁定当前用户、可选邮箱凭据和密码凭据，确认事务外读取结果仍然有效
        AuthUserDO user = userId == null ? null : authUserMapper.selectByUserIdForUpdate(userId);
        if (!isActive(user)) {
            return PasswordLoginCompletion.failed();
        }
        if (emailKey != null) {
            EmailCredentialDO emailCredential = emailCredentialMapper.selectByEmailKeyForUpdate(emailKey);
            if (emailCredential == null || !userId.equals(emailCredential.getUserId())) {
                return PasswordLoginCompletion.failed();
            }
        }
        PasswordCredentialDO credential = passwordCredentialMapper.selectByUserIdForUpdate(userId);
        LocalDateTime now = LocalDateTime.now();
        if (credential == null || isPasswordLocked(credential, now)) {
            return PasswordLoginCompletion.failed();
        }
        if (!Objects.equals(credential.getPasswordHash(), verifiedPasswordHash)) {
            return PasswordLoginCompletion.retry();
        }

        // 2. 根据已确认的事务外校验结果更新失败状态或建立登录态
        if (!passwordMatched) {
            recordPasswordFailure(credential, now);
            return PasswordLoginCompletion.failed();
        }
        resetPasswordFailureState(credential, upgradedPasswordHash, now);
        TenantMemberDO member = tenantMemberMapper.selectActiveByTenantIdAndUserIdForUpdate(
                user.getDefaultTenantId(), user.getUserId());
        if (member == null || member.getMemberStatus() != TenantMemberStatus.ACTIVE.getCode()) {
            return PasswordLoginCompletion.failed();
        }
        sessionService.establishLoginAfterCommit(user.getUserId(), user.getDefaultTenantId());
        securityAuditService.recordSuccess(user.getUserId(), emailKey == null ? "ACCOUNT_PASSWORD_LOGIN" : "EMAIL_PASSWORD_LOGIN",
                emailKey == null ? "已通过账号密码登录" : "已通过邮箱密码登录");
        return PasswordLoginCompletion.succeeded(currentUserProfileService.getProfile(user.getUserId(), user.getDefaultTenantId()));
    }

    private void recordPasswordFailure(PasswordCredentialDO credential, LocalDateTime now) {
        int failedAttempts = credential.getFailedAttempts() == null ? 1 : credential.getFailedAttempts() + 1;
        credential.setFailedAttempts(failedAttempts);
        if (failedAttempts >= MAX_CONSECUTIVE_PASSWORD_FAILURES) {
            credential.setPasswordLockedUntil(now.plusMinutes(PASSWORD_LOCK_MINUTES));
        }
        credential.setUpdateTime(now);
        passwordCredentialMapper.updateById(credential);
    }

    private void resetPasswordFailureState(PasswordCredentialDO credential, String upgradedPasswordHash, LocalDateTime now) {
        boolean stateChanged = credential.getFailedAttempts() != null && credential.getFailedAttempts() != 0
                || credential.getPasswordLockedUntil() != null || upgradedPasswordHash != null;
        if (!stateChanged) {
            return;
        }
        credential.setFailedAttempts(0);
        credential.setPasswordLockedUntil(null);
        if (upgradedPasswordHash != null) {
            credential.setPasswordHash(upgradedPasswordHash);
            credential.setPasswordChangedTime(now);
        }
        credential.setUpdateTime(now);
        passwordCredentialMapper.updateById(credential);
    }

    private boolean isActive(AuthUserDO user) {
        return user != null && user.getStatus() != null && user.getStatus() == UserStatus.ACTIVE.getCode()
                && user.getDefaultTenantId() != null && !user.getDefaultTenantId().isBlank();
    }

    private boolean isPasswordLocked(PasswordCredentialDO credential, LocalDateTime now) {
        return credential.getPasswordLockedUntil() != null && credential.getPasswordLockedUntil().isAfter(now);
    }

    /**
     * 密码登录短事务的完成结果。
     *
     * @param session 成功登录的会话摘要；失败或重试时为空
     * @param shouldRetry 密码凭据在事务外校验后发生变化时为 true
     */
    public record PasswordLoginCompletion(LoginSessionVO session, boolean shouldRetry) {

        static PasswordLoginCompletion succeeded(LoginSessionVO session) {
            return new PasswordLoginCompletion(session, false);
        }

        static PasswordLoginCompletion failed() {
            return new PasswordLoginCompletion(null, false);
        }

        static PasswordLoginCompletion retry() {
            return new PasswordLoginCompletion(null, true);
        }
    }
}
