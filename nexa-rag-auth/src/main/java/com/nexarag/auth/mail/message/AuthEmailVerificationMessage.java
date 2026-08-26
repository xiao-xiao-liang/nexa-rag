package com.nexarag.auth.mail.message;

import com.nexarag.auth.enums.EmailVerificationPurpose;

/**
 * 已提交的认证邮箱验证码消息；邮箱和验证码均为 AES-GCM 密文。
 *
 * @param challengeId 挑战 ID，作为幂等追踪键
 * @param emailCiphertext 加密后的收件邮箱
 * @param purpose 验证码用途
 * @param verificationCodeCiphertext 加密后的验证码
 */
public record AuthEmailVerificationMessage(Long challengeId, String emailCiphertext,
                                           EmailVerificationPurpose purpose, String verificationCodeCiphertext) {
}
