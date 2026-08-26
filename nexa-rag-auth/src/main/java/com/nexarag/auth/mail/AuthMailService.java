package com.nexarag.auth.mail;

import com.nexarag.auth.enums.EmailVerificationPurpose;

/**
 * 认证邮件投递服务，仅发送验证码正文，不承载业务状态。
 */
public interface AuthMailService {

    /**
     * 向指定邮箱投递验证码。
     *
     * @param email 收件邮箱
     * @param purpose 验证码用途
     * @param verificationCode 六位验证码
     */
    void sendVerificationCode(String email, EmailVerificationPurpose purpose, String verificationCode);
}
