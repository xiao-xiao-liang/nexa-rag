package com.nexarag.auth.service;

/**
 * 管理仅绑定当前 Sa-Token Token-Session 的短期二次验证授权。
 */
public interface RecentVerificationService {

    /** 签发当前登录会话的最近验证授权。 */
    void grantForCurrentSession();

    /**
     * 判断当前登录会话是否仍拥有未过期授权。
     *
     * @return 授权有效时返回 true
     */
    boolean hasCurrentSessionGrant();

    /**
     * 判断指定 Sa-Token 登录态是否仍拥有未过期的最近验证授权。
     *
     * <p>仅供 OAuth 绑定回调使用：跨站回调在 SameSite=Strict 下不会携带原 Cookie，
     * 因而必须通过服务端 state 中保存的原 Token-Session 复验。</p>
     *
     * @param tokenValue 发起绑定时的 Sa-Token 值
     * @return 授权有效时返回 true
     */
    boolean hasGrantForToken(String tokenValue);

    /** 当前会话缺少授权时拒绝敏感操作。 */
    void requireCurrentSessionGrant();

    /**
     * 指定登录态缺少授权时拒绝敏感操作。
     *
     * @param tokenValue 发起绑定时的 Sa-Token 值
     */
    void requireGrantForToken(String tokenValue);

    /** 撤销当前会话最近验证授权。 */
    void revokeCurrentSessionGrant();
}
