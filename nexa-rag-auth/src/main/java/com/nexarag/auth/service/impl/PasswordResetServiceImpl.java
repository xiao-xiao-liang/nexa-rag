package com.nexarag.auth.service.impl;

import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.model.dto.PasswordResetDTO;
import com.nexarag.auth.service.PasswordResetService;
import com.nexarag.auth.service.PasswordService;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 基于邮箱验证码的密码重置实现，成功后撤销用户所有既有登录态。
 */
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final PasswordService passwordService;
    private final PasswordCredentialMutationService credentialMutationService;

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetPassword(PasswordResetDTO resetDTO) {
        // 1. 在任何数据库事务和行锁外完成密码规则校验与 Argon2id 哈希
        if (resetDTO == null) {
            throw authenticationFailed();
        }
        String passwordHash = passwordService.hash(resetDTO.getNewPassword());

        // 2. 仅在短事务内锁定用户、消费验证码并持久化已生成的哈希
        credentialMutationService.resetPassword(resetDTO, passwordHash);
    }

    /**
     * 创建不暴露账号与邮箱状态的统一未认证异常。
     */
    private ClientException authenticationFailed() {
        return ClientException.unauthorized(AuthErrorCode.AUTHENTICATION_FAILED);
    }
}
