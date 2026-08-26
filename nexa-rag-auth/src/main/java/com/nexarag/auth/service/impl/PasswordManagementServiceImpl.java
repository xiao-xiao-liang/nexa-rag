package com.nexarag.auth.service.impl;

import com.nexarag.auth.context.UserContext;
import com.nexarag.auth.enums.UserStatus;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.mapper.EmailCredentialMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.model.dataobject.EmailCredentialDO;
import com.nexarag.auth.model.dto.EmailCodeSendDTO;
import com.nexarag.auth.model.dto.PasswordSetDTO;
import com.nexarag.auth.model.vo.EmailChallengeVO;
import com.nexarag.auth.service.PasswordManagementService;
import com.nexarag.auth.service.PasswordService;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Locale;

/**
 * 已登录用户的密码设置实现，使用当前绑定邮箱验证码确认高风险操作。
 */
@Service
@RequiredArgsConstructor
public class PasswordManagementServiceImpl implements PasswordManagementService {

    private final AuthUserMapper authUserMapper;
    private final EmailCredentialMapper emailCredentialMapper;
    private final PasswordService passwordService;
    private final PasswordCredentialMutationService credentialMutationService;

    /**
     * {@inheritDoc}
     */
    @Override
    public EmailChallengeVO sendPasswordSetCode(EmailCodeSendDTO sendDTO) {
        // 1. 只读确认当前用户和绑定邮箱；换绑竞态会由后续验证码消费事务拒绝
        if (sendDTO == null) {
            throw authenticationFailed();
        }
        AuthUserDO user = requireCurrentUserWithBoundEmail(sendDTO.getEmail());

        // 2. 将验证码用途和稳定用户ID绑定，供密码设置接口单次消费
        return credentialMutationService.sendSensitiveOperationCode(sendDTO.getEmail().trim(), user.getUserId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPassword(PasswordSetDTO setDTO) {
        // 1. 从 Sa-Token 读取稳定用户 ID，并在锁外完成 Argon2id 哈希
        if (setDTO == null) {
            throw authenticationFailed();
        }
        Long userId = currentUserId();
        String passwordHash = passwordService.hash(setDTO.getNewPassword());

        // 2. 仅在短事务内锁定用户、消费验证码并持久化密码哈希
        credentialMutationService.setPassword(userId, setDTO, passwordHash);
    }

    /**
     * 从 Sa-Token 上下文读取当前启用用户；最终状态由短事务再次校验。
     */
    private AuthUserDO currentActiveUser() {
        long userId = currentUserId();
        AuthUserDO user = authUserMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != UserStatus.ACTIVE.getCode()) {
            throw authenticationFailed();
        }
        return user;
    }

    /**
     * 从 Sa-Token 上下文读取稳定用户 ID。
     */
    private Long currentUserId() {
        try {
            return Long.parseLong(UserContext.getUserId());
        } catch (NumberFormatException exception) {
            throw authenticationFailed();
        }
    }

    /**
     * 确认请求邮箱属于当前启用用户；最终归属由短事务再次校验。
     *
     * @param email 请求中的邮箱地址
     * @return 当前启用用户
     */
    private AuthUserDO requireCurrentUserWithBoundEmail(String email) {
        AuthUserDO user = currentActiveUser();
        EmailCredentialDO emailCredential = emailCredentialMapper.selectByEmailKey(normalizeEmail(email));
        if (emailCredential == null || !user.getUserId().equals(emailCredential.getUserId())) {
            throw authenticationFailed();
        }
        return user;
    }

    /**
     * 规范化邮箱唯一键。
     */
    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 创建不暴露账号与邮箱状态的统一未认证异常。
     */
    private ClientException authenticationFailed() {
        return ClientException.unauthorized(AuthErrorCode.AUTHENTICATION_FAILED);
    }
}
