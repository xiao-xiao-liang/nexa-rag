package com.nexarag.auth.enums;

/**
 * 邮箱验证码业务用途，与挑战记录的 purpose_code 字段保持一致。
 */
public enum EmailVerificationPurpose {

    /** 新账号注册并验证首个邮箱。 */
    REGISTER,

    /** 使用邮箱验证码登录。 */
    EMAIL_LOGIN,

    /** 通过邮箱验证码重置密码。 */
    PASSWORD_RESET,

    /** 验证待替换的旧邮箱。 */
    CHANGE_EMAIL_OLD,

    /** 验证待绑定的新邮箱。 */
    CHANGE_EMAIL_NEW,

    /** 默认管理员首次激活。 */
    BOOTSTRAP_ADMIN_ACTIVATE,

    /** 高风险账号操作的最近验证。 */
    SENSITIVE_OPERATION
}
