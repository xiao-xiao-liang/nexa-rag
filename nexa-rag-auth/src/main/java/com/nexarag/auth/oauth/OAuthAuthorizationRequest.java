package com.nexarag.auth.oauth;

/**
 * 创建第三方授权 URL 时的通用输入。
 *
 * @param state 一次性且不可预测的服务端关联值
 * @param redirectUri 在平台后台精确登记的回调地址
 * @param pkceChallenge RFC 7636 S256 挑战值；不支持 PKCE 的平台为空
 */
public record OAuthAuthorizationRequest(String state, String redirectUri, String pkceChallenge) {
}
