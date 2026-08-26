package com.nexarag.auth.service;

import com.nexarag.auth.model.dto.PasswordResetDTO;

/**
 * 密码重置服务，使用当前绑定邮箱验证码更新本地密码并撤销会话。
 */
public interface PasswordResetService {

    /**
     * 重置本地密码，不自动建立新的登录态。
     *
     * @param resetDTO 密码重置请求
     */
    void resetPassword(PasswordResetDTO resetDTO);
}
