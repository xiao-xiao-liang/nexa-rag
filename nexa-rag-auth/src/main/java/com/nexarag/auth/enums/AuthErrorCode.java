package com.nexarag.auth.enums;

import com.nexarag.common.error.IErrorCode;
import lombok.RequiredArgsConstructor;

/**
 * 认证与授权模块错误码。
 */
@RequiredArgsConstructor
public enum AuthErrorCode implements IErrorCode {

    /** 账号名不符合规范。 */
    ACCOUNT_NAME_INVALID("A000001", "账号名不符合规范"),

    /** 账号名已被占用。 */
    ACCOUNT_NAME_CONFLICT("A000002", "账号名已被占用"),

    /** 用户不存在。 */
    USER_NOT_FOUND("A000003", "用户不存在"),

    /** 用户已被禁用。 */
    USER_DISABLED("A000004", "用户已被禁用"),

    /** 租户成员关系不存在或不可用。 */
    TENANT_MEMBERSHIP_UNAVAILABLE("A000005", "租户成员关系不可用"),

    /** 本地密码不符合安全规则。 */
    PASSWORD_POLICY_INVALID("A000006", "密码不符合安全要求"),

    /** 登录信息无效，不暴露账号或凭据的实际状态。 */
    AUTHENTICATION_FAILED("A000007", "账号或密码错误"),

    /** 邮箱验证码发送过于频繁。 */
    EMAIL_CODE_SEND_TOO_FREQUENT("A000008", "验证码发送过于频繁，请稍后再试"),

    /** 邮箱验证码当日发送次数已用尽。 */
    EMAIL_CODE_DAILY_LIMIT_EXCEEDED("A000009", "该邮箱今日验证码发送次数已达上限"),

    /** 邮箱验证码无效、已过期或已消费。 */
    EMAIL_CODE_INVALID("A000010", "验证码无效或已过期"),

    /** 注册账号名或邮箱已被占用。 */
    REGISTRATION_CONFLICT("A000011", "账号名或邮箱已被占用"),

    /** 尚未建立有效登录态。 */
    AUTHENTICATION_REQUIRED("A000012", "请先登录"),

    /** 已登录主体不具备访问目标资源的权限。 */
    ACCESS_DENIED("A000013", "无权访问该资源"),

    /** 请求未通过 CSRF 或同源安全校验。 */
    CSRF_VALIDATION_FAILED("A000014", "请求安全校验失败"),

    /** 邮箱已被其他账号验证绑定。 */
    EMAIL_CONFLICT("A000015", "邮箱已被占用"),

    /** 当前会话尚未完成所需的二次验证。 */
    RECENT_VERIFICATION_REQUIRED("A000016", "请先完成身份验证"),

    /** 第三方提供方未启用或不受支持。 */
    OAUTH_PROVIDER_UNAVAILABLE("A000017", "第三方登录暂不可用"),

    /** OAuth 回调状态无效、过期或已消费。 */
    OAUTH_STATE_INVALID("A000018", "第三方登录状态无效或已过期"),

    /** 第三方身份已绑定到其他账号。 */
    EXTERNAL_IDENTITY_CONFLICT("A000019", "该第三方账号已绑定其他用户"),

    /** 不允许解绑最后一种可用登录凭据。 */
    LAST_LOGIN_CREDENTIAL_PROTECTED("A000020", "至少保留一种登录方式"),

    /** 第三方平台拒绝授权、授权码无效或身份信息无法验证。 */
    OAUTH_AUTHORIZATION_FAILED("A000021", "第三方授权失败，请重新发起登录"),

    /** 租户名称已被占用。 */
    TENANT_NAME_CONFLICT("A000022", "租户名称已被占用"),

    /** 租户邀请或所有者转交状态不可用。 */
    TENANT_OPERATION_INVALID("A000023", "租户操作状态无效"),

    /** 浏览器请求来源与服务端允许来源不匹配。 */
    REQUEST_ORIGIN_VALIDATION_FAILED("A000024", "请求来源校验失败"),

    /** 浏览器 Fetch Metadata 表明请求来自不受信任的跨站上下文。 */
    FETCH_METADATA_VALIDATION_FAILED("A000025", "浏览器请求上下文校验失败"),

    /** CSRF 请求头缺失、失效或与当前会话不匹配。 */
    CSRF_TOKEN_VALIDATION_FAILED("A000026", "CSRF 令牌校验失败");

    /** 错误码。 */
    private final String code;

    /** 错误信息。 */
    private final String message;

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    @Override
    public String code() {
        return code;
    }

    /**
     * 获取错误信息。
     *
     * @return 错误信息
     */
    @Override
    public String message() {
        return message;
    }
}
