package com.nexarag.auth.service.impl;

import com.nexarag.auth.enums.EmailVerificationPurpose;
import com.nexarag.auth.enums.UserStatus;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.mapper.EmailCredentialMapper;
import com.nexarag.auth.mapper.PasswordCredentialMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.model.dataobject.EmailCredentialDO;
import com.nexarag.auth.model.dataobject.PasswordCredentialDO;
import com.nexarag.auth.model.dto.PasswordResetDTO;
import com.nexarag.auth.model.dto.PasswordSetDTO;
import com.nexarag.auth.model.vo.EmailChallengeVO;
import com.nexarag.auth.service.EmailChallengeService;
import com.nexarag.auth.service.SessionService;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 密码凭据短事务写入服务，避免在数据库锁内执行 Argon2 哈希。
 */
@Service
@RequiredArgsConstructor
public class PasswordCredentialMutationService {

    private final EmailCredentialMapper emailCredentialMapper;
    private final AuthUserMapper authUserMapper;
    private final PasswordCredentialMapper passwordCredentialMapper;
    private final EmailChallengeService emailChallengeService;
    private final SessionService sessionService;

    /**
     * 创建绑定当前用户的敏感操作验证码挑战。
     *
     * @param email 当前绑定邮箱
     * @param userId 当前用户 ID
     * @return 验证码挑战摘要
     */
    public EmailChallengeVO sendSensitiveOperationCode(String email, Long userId) {
        return emailChallengeService.sendCode(email, EmailVerificationPurpose.SENSITIVE_OPERATION, userId);
    }

    /**
     * 在短事务内消费重置验证码并写入事务外已经计算的密码哈希。
     *
     * @param resetDTO 密码重置请求
     * @param passwordHash 事务外计算的 Argon2id 哈希
     */
    @Transactional(noRollbackFor = ClientException.class)
    public void resetPassword(PasswordResetDTO resetDTO, String passwordHash) {
        // 1. 锁定当前邮箱和用户，避免换绑或禁用后继续重置密码
        AuthUserDO user = requireActiveUserByBoundEmail(resetDTO.getEmail());

        // 2. 消费绑定邮箱和用户的重置验证码
        emailChallengeService.verifyAndConsume(resetDTO.getChallengeId(), resetDTO.getEmail(),
                EmailVerificationPurpose.PASSWORD_RESET, user.getUserId(), resetDTO.getVerificationCode());

        // 3. 写入已生成的密码哈希并清除失败冻结状态
        savePasswordCredential(user.getUserId(), passwordHash);

        // 4. 数据提交后撤销全部历史登录态，重置接口不自动登录
        sessionService.revokeAllSessionsAfterCommit(user.getUserId());
    }

    /**
     * 在短事务内消费敏感操作验证码并写入事务外已经计算的密码哈希。
     *
     * @param userId 当前 Sa-Token 用户 ID
     * @param setDTO 设置密码请求
     * @param passwordHash 事务外计算的 Argon2id 哈希
     */
    @Transactional(noRollbackFor = ClientException.class)
    public void setPassword(Long userId, PasswordSetDTO setDTO, String passwordHash) {
        // 1. 锁定当前用户和邮箱归属，防止会话过期或邮箱换绑后的竞态写入
        AuthUserDO user = requireActiveUserById(userId);
        EmailCredentialDO emailCredential = emailCredentialMapper.selectByEmailKeyForUpdate(normalizeEmail(setDTO.getEmail()));
        if (emailCredential == null || !user.getUserId().equals(emailCredential.getUserId())) {
            throw authenticationFailed();
        }

        // 2. 消费敏感操作验证码并写入已生成密码哈希
        emailChallengeService.verifyAndConsume(setDTO.getChallengeId(), setDTO.getEmail(),
                EmailVerificationPurpose.SENSITIVE_OPERATION, user.getUserId(), setDTO.getVerificationCode());
        savePasswordCredential(user.getUserId(), passwordHash);
    }

    private AuthUserDO requireActiveUserByBoundEmail(String email) {
        EmailCredentialDO emailCredential = emailCredentialMapper.selectByEmailKeyForUpdate(normalizeEmail(email));
        AuthUserDO user = emailCredential == null ? null : authUserMapper.selectByUserIdForUpdate(emailCredential.getUserId());
        if (!isActive(user)) {
            throw authenticationFailed();
        }
        return user;
    }

    private AuthUserDO requireActiveUserById(Long userId) {
        AuthUserDO user = userId == null ? null : authUserMapper.selectByUserIdForUpdate(userId);
        if (!isActive(user)) {
            throw authenticationFailed();
        }
        return user;
    }

    private void savePasswordCredential(Long userId, String passwordHash) {
        LocalDateTime now = LocalDateTime.now();
        PasswordCredentialDO credential = passwordCredentialMapper.selectByUserIdForUpdate(userId);
        if (credential == null) {
            passwordCredentialMapper.insert(new PasswordCredentialDO(userId, passwordHash, 0,
                    null, now, now, now));
            return;
        }
        credential.setPasswordHash(passwordHash);
        credential.setFailedAttempts(0);
        credential.setPasswordLockedUntil(null);
        credential.setPasswordChangedTime(now);
        credential.setUpdateTime(now);
        passwordCredentialMapper.updateById(credential);
    }

    private boolean isActive(AuthUserDO user) {
        return user != null && user.getStatus() != null && user.getStatus() == UserStatus.ACTIVE.getCode();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private ClientException authenticationFailed() {
        return ClientException.unauthorized(AuthErrorCode.AUTHENTICATION_FAILED);
    }
}
