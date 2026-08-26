package com.nexarag.auth.oauth.client;

import com.nexarag.auth.enums.OAuthProvider;
import com.nexarag.auth.oauth.OAuthAuthorizationRequest;
import com.nexarag.auth.oauth.OAuthPrincipal;

/**
 * 单一第三方 OAuth 提供方的协议适配器。
 */
public interface OAuthProviderClient {

    /**
     * 返回此客户端支持的提供方。
     *
     * @return 提供方枚举
     */
    OAuthProvider provider();

    /**
     * 判断提供方是否支持 RFC 7636 PKCE。
     *
     * @return 支持时返回 true
     */
    boolean supportsPkce();

    /**
     * 构造供浏览器跳转的官方授权地址。
     *
     * @param request 已完成 state 和回调地址校验的授权请求
     * @return 授权地址
     */
    String createAuthorizationUrl(OAuthAuthorizationRequest request);

    /**
     * 用回调授权码交换并解析稳定主体。
     *
     * @param authorizationCode 平台回调的单次授权码
     * @param redirectUri 与授权请求完全一致的回调地址
     * @param pkceVerifier PKCE verifier；未启用 PKCE 的平台为空
     * @return 已验证的第三方稳定主体
     */
    OAuthPrincipal resolvePrincipal(String authorizationCode, String redirectUri, String pkceVerifier);
}
