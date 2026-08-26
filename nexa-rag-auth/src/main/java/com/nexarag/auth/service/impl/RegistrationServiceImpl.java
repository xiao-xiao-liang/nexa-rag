package com.nexarag.auth.service.impl;

import com.nexarag.auth.enums.EmailVerificationPurpose;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.mapper.EmailCredentialMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.model.dataobject.EmailCredentialDO;
import com.nexarag.auth.model.dto.RegisterAccountDTO;
import com.nexarag.auth.model.vo.LoginSessionVO;
import com.nexarag.auth.service.AccountNamePolicy;
import com.nexarag.auth.service.AuthUserProvisioningService;
import com.nexarag.auth.service.EmailChallengeService;
import com.nexarag.auth.service.CurrentUserProfileService;
import com.nexarag.auth.service.RegistrationService;
import com.nexarag.auth.service.SecurityAuditService;
import com.nexarag.auth.service.SessionService;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 无密码注册实现，原子创建用户、已验证邮箱和默认租户成员关系。
 */
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final AccountNamePolicy accountNamePolicy;
    private final AuthUserProvisioningService authUserProvisioningService;
    private final EmailChallengeService emailChallengeService;
    private final AuthUserMapper authUserMapper;
    private final EmailCredentialMapper emailCredentialMapper;
    private final SessionService sessionService;
    private final CurrentUserProfileService currentUserProfileService;
    private final SecurityAuditService securityAuditService;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(noRollbackFor = ClientException.class)
    public LoginSessionVO register(RegisterAccountDTO registerDTO) {
        // 1. 校验账号名、邮箱唯一性和预置角色，避免无效注册消耗验证码
        if (registerDTO == null) {
            throw new ClientException(AuthErrorCode.ACCOUNT_NAME_INVALID);
        }
        String accountNameKey = accountNamePolicy.normalizeAndValidate(registerDTO.getAccountName());
        String accountName = registerDTO.getAccountName().trim();
        String emailKey = normalizeEmail(registerDTO.getEmail());
        if (authUserMapper.selectByAccountNameKey(accountNameKey) != null
                || emailCredentialMapper.selectByEmailKey(emailKey) != null) {
            throw new ClientException(AuthErrorCode.REGISTRATION_CONFLICT);
        }
        // 2. 单次消费注册验证码，消费动作会加入当前事务
        emailChallengeService.verifyAndConsume(registerDTO.getChallengeId(), registerDTO.getEmail(),
                EmailVerificationPurpose.REGISTER, null, registerDTO.getVerificationCode());

        // 3. 原子写入用户、首个已验证邮箱和默认租户成员关系
        try {
            AuthUserDO user = authUserProvisioningService.createDefaultTenantUser(accountName);
            LocalDateTime now = LocalDateTime.now();
            emailCredentialMapper.insert(new EmailCredentialDO(user.getUserId(), registerDTO.getEmail().trim(), emailKey,
                    now, now, now));
            sessionService.establishLoginAfterCommit(user.getUserId(), user.getDefaultTenantId(), true);
            securityAuditService.recordSuccess(user.getUserId(), "ACCOUNT_REGISTER_LOGIN", "注册账号并自动登录");
            return currentUserProfileService.getProfile(user.getUserId(), user.getDefaultTenantId());
        } catch (DuplicateKeyException exception) {
            // 并发注册由数据库唯一约束最终裁决；回滚验证码消费，允许调用方重新申请并注册。
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw new ClientException(AuthErrorCode.REGISTRATION_CONFLICT);
        }
    }

    /**
     * 注册用邮箱规范化，确保唯一键与验证码上下文使用相同规则。
     */
    private String normalizeEmail(String email) {
        if (email == null) {
            throw new ClientException(AuthErrorCode.EMAIL_CODE_INVALID);
        }
        String emailKey = email.trim().toLowerCase(Locale.ROOT);
        int atIndex = emailKey.lastIndexOf('@');
        if (emailKey.length() > 320 || atIndex <= 0 || atIndex == emailKey.length() - 1 || emailKey.indexOf(' ') >= 0) {
            throw new ClientException(AuthErrorCode.EMAIL_CODE_INVALID);
        }
        return emailKey;
    }

}
