package com.nexarag.boot.constants;

/**
 * 后端鉴权规则使用的 API 路径与通配模式。
 */
public final class AuthApiPathConstant {

    public static final String API_ALL = "/api/**";
    public static final String ALL = "/**";
    public static final String AUTH_ACCOUNT_LOGIN = "/api/auth/login/account";
    public static final String AUTH_EMAIL_PASSWORD_LOGIN = "/api/auth/login/email-password";
    public static final String AUTH_EMAIL_CODE_LOGIN = "/api/auth/login/email-code";
    public static final String AUTH_REGISTER = "/api/auth/register";
    public static final String AUTH_SEND_EMAIL_CODE = "/api/auth/email/send-code";
    public static final String AUTH_PASSWORD_RESET = "/api/auth/password/reset";
    public static final String AUTH_CSRF_TOKEN = "/api/auth/csrf-token";
    public static final String AUTH_OAUTH_START = "/api/auth/oauth/*/start";
    public static final String AUTH_OAUTH_CALLBACK = "/api/auth/oauth/*/callback";
    public static final String MODEL_ALL = "/api/model/**";
    public static final String MODEL_PROMPT_ALL = "/api/model/prompts/**";
    public static final String MODEL_OBSERVABILITY_ALL = "/api/model-observability/**";
    public static final String CRM_ALL = "/api/crm/**";
    public static final String ERROR = "/error";
    public static final String FAVICON = "/favicon.ico";

    private AuthApiPathConstant() {
    }
}
