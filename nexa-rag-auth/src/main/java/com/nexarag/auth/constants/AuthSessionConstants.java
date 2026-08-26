package com.nexarag.auth.constants;

/**
 * Sa-Token Token-Session 中认证业务数据的键名。
 */
public final class AuthSessionConstants {

    /** 当前 Token 所选择的租户ID。 */
    public static final String CURRENT_TENANT_ID = "currentTenantId";

    /** 与当前 Sa-Token 登录态绑定的 CSRF 挑战。 */
    public static final String CSRF_TOKEN = "csrfToken";

    /** 当前 Token-Session 最近验证授权的到期时间戳。 */
    public static final String RECENT_VERIFICATION_EXPIRES_AT = "recentVerificationExpiresAt";

    private AuthSessionConstants() {
    }
}
