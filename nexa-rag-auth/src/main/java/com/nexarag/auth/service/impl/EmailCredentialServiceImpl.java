package com.nexarag.auth.service.impl;

import com.nexarag.auth.context.UserContext;
import com.nexarag.auth.enums.EmailVerificationPurpose;
import com.nexarag.auth.enums.UserStatus;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.model.dto.EmailChangeDTO;
import com.nexarag.auth.model.dto.EmailCodeSendDTO;
import com.nexarag.auth.model.dto.EmailVerificationDTO;
import com.nexarag.auth.model.vo.EmailChallengeVO;
import com.nexarag.auth.service.EmailChallengeService;
import com.nexarag.auth.service.AuthIdentityBloomFilterService;
import com.nexarag.auth.service.EmailCredentialService;
import com.nexarag.auth.service.RecentVerificationService;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 当前账号邮箱凭据实现，保证首绑与换绑均由服务端验证码和唯一约束共同裁决。
 */
@Service
@RequiredArgsConstructor
public class EmailCredentialServiceImpl implements EmailCredentialService {

    private final AuthUserMapper authUserMapper;
    private final EmailChallengeService emailChallengeService;
    private final RecentVerificationService recentVerificationService;
    private final AuthIdentityBloomFilterService bloomFilterService;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(noRollbackFor = ClientException.class)
    public EmailChallengeVO sendEmailChangeCode(EmailCodeSendDTO sendDTO) {
        // 1. 确认当前用户与明确的邮箱换绑用途
        if (sendDTO == null || !isChangeEmailPurpose(sendDTO.getPurpose())) {
            throw authenticationFailed();
        }
        AuthUserDO user = requireCurrentActiveUser();
        String emailKey = normalizeEmail(sendDTO.getEmail());

        // 2. 旧邮箱必须是当前凭据；新邮箱不得已被任何账号占用
        if (sendDTO.getPurpose() == EmailVerificationPurpose.CHANGE_EMAIL_OLD) {
            if (!emailKey.equals(user.getEmail())) {
                throw authenticationFailed();
            }
        } else {
            if (emailKey.equals(user.getEmail())) {
                throw new ClientException(AuthErrorCode.EMAIL_CONFLICT);
            }
            if (authUserMapper.selectByEmailForUpdate(emailKey) != null) {
                throw new ClientException(AuthErrorCode.EMAIL_CONFLICT);
            }
        }

        // 3. 将挑战绑定稳定用户ID，后续只能由当前账号消费
        return emailChallengeService.sendCode(sendDTO.getEmail().trim(), sendDTO.getPurpose(), user.getUserId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void bindFirstEmail(EmailVerificationDTO verificationDTO) {
        // 1. 首个邮箱绑定必须已有当前会话最近验证授权
        recentVerificationService.requireCurrentSessionGrant();
        AuthUserDO user = requireCurrentActiveUser();
        if (verificationDTO == null) {
            throw authenticationFailed();
        }
        if (user.getEmail() != null) {
            throw new ClientException(AuthErrorCode.EMAIL_CONFLICT);
        }

        // 2. 验证新邮箱归属并写入唯一凭据
        String emailKey = normalizeEmail(verificationDTO.getEmail());
        if (authUserMapper.selectByEmailForUpdate(emailKey) != null) {
            throw new ClientException(AuthErrorCode.EMAIL_CONFLICT);
        }
        emailChallengeService.verifyAndConsume(verificationDTO.getChallengeId(), verificationDTO.getEmail(),
                EmailVerificationPurpose.CHANGE_EMAIL_NEW, user.getUserId(), verificationDTO.getVerificationCode());
        updateUserEmail(user, emailKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void changeEmail(EmailChangeDTO changeDTO) {
        // 1. 锁定当前邮箱凭据，并确认请求中的旧邮箱与当前归属一致
        AuthUserDO user = requireCurrentActiveUser();
        if (changeDTO == null || changeDTO.getOldEmailVerification() == null
                || changeDTO.getNewEmailVerification() == null) {
            throw authenticationFailed();
        }
        EmailVerificationDTO oldVerification = changeDTO.getOldEmailVerification();
        EmailVerificationDTO newVerification = changeDTO.getNewEmailVerification();
        String oldEmailKey = normalizeEmail(oldVerification.getEmail());
        String newEmailKey = normalizeEmail(newVerification.getEmail());
        if (!oldEmailKey.equals(user.getEmail()) || oldEmailKey.equals(newEmailKey)) {
            throw authenticationFailed();
        }
        if (authUserMapper.selectByEmailForUpdate(newEmailKey) != null) {
            throw new ClientException(AuthErrorCode.EMAIL_CONFLICT);
        }

        // 2. 在同一数据库事务中消费两枚独立用途的验证码
        emailChallengeService.verifyAndConsume(oldVerification.getChallengeId(), oldVerification.getEmail(),
                EmailVerificationPurpose.CHANGE_EMAIL_OLD, user.getUserId(), oldVerification.getVerificationCode());
        emailChallengeService.verifyAndConsume(newVerification.getChallengeId(), newVerification.getEmail(),
                EmailVerificationPurpose.CHANGE_EMAIL_NEW, user.getUserId(), newVerification.getVerificationCode());

        // 3. 替换 auth_user 上的唯一邮箱；旧邮箱立即不再具备登录与重置资格
        try {
            updateUserEmail(user, newEmailKey);
        } catch (DuplicateKeyException exception) {
            throw new ClientException(AuthErrorCode.EMAIL_CONFLICT);
        }
    }

    /**
     * 锁定并获取当前 Sa-Token 对应的启用用户。
     */
    private AuthUserDO requireCurrentActiveUser() {
        try {
            AuthUserDO user = authUserMapper.selectByUserIdForUpdate(Long.valueOf(UserContext.getUserId()));
            if (user != null && user.getStatus() != null && user.getStatus() == UserStatus.ACTIVE.getCode()) {
                return user;
            }
        } catch (NumberFormatException exception) {
            // 非法登录主体不能参与任何邮箱凭据变更。
        }
        throw authenticationFailed();
    }

    /**
     * 写入当前用户的已验证邮箱。
     */
    private void updateUserEmail(AuthUserDO user, String emailKey) {
        LocalDateTime now = LocalDateTime.now();
        try {
            bloomFilterService.recordEmailOwner(user.getUserId(), emailKey);
            user.setEmail(emailKey);
            user.setEmailVerifiedTime(now);
            user.setUpdateTime(now);
            authUserMapper.updateById(user);
        } catch (DuplicateKeyException exception) {
            throw new ClientException(AuthErrorCode.EMAIL_CONFLICT);
        }
    }

    /**
     * 判断是否是双邮箱验证允许的用途。
     */
    private boolean isChangeEmailPurpose(EmailVerificationPurpose purpose) {
        return purpose == EmailVerificationPurpose.CHANGE_EMAIL_OLD
                || purpose == EmailVerificationPurpose.CHANGE_EMAIL_NEW;
    }

    /**
     * 规范化邮箱唯一键。
     */
    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw authenticationFailed();
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 返回不暴露账号或邮箱归属状态的认证失败异常。
     */
    private ClientException authenticationFailed() {
        return ClientException.unauthorized(AuthErrorCode.AUTHENTICATION_FAILED);
    }
}
