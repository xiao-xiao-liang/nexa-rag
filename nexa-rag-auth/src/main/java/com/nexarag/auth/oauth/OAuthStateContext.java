package com.nexarag.auth.oauth;

import com.nexarag.auth.enums.OAuthAction;
import com.nexarag.auth.enums.OAuthProvider;

/**
 * OAuth state 服务端保存的单次授权上下文。
 *
 * <p>绑定流程保留发起操作的 Token 值，仅用于回调时通过 Sa-Token 验证该登录态仍属于同一用户；
 * 该值只存 Redis，不会返回浏览器、写入日志或持久化到数据库。</p>
 *
 * @param provider 第三方身份提供方
 * @param action 当前流程动作
 * @param bindingUserId 发起绑定的本地用户 ID；登录流程为空
 * @param bindingTokenValue 发起绑定时的 Sa-Token 值；登录流程为空
 * @param accountName 首次第三方登录注册时预先输入的账号名；已有绑定登录时为空
 * @param pkceVerifier PKCE verifier；未启用 PKCE 的平台为空
 * @param redirectUri 发起授权时使用的精确回调地址，换码时必须原样复用
 */
public record OAuthStateContext(OAuthProvider provider, OAuthAction action, Long bindingUserId,
                                String bindingTokenValue, String accountName, String pkceVerifier, String redirectUri) {
}
