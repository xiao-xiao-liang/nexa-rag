package com.nexarag.auth.oauth.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexarag.auth.config.OAuthProviderProperties;
import com.nexarag.auth.enums.OAuthProvider;
import com.nexarag.auth.oauth.OAuthAuthorizationRequest;
import com.nexarag.auth.oauth.OAuthPrincipal;
import org.springframework.stereotype.Component;

/**
 * QQ 互联授权码客户端。
 *
 * <p>QQ 的 Token 与 OpenID 接口均是查询参数协议，稳定主体只能取 {@code /me} 返回的 {@code openid}；
 * 不申请昵称等资料权限，也不调用用户资料接口。</p>
 */
@Component
public class QqOAuthProviderClient extends AbstractOAuthProviderClient {

    /** QQ 互联授权端点。 */
    private static final String AUTHORIZATION_URL = "https://graph.qq.com/oauth2.0/authorize";

    /** QQ 互联 Token 端点。 */
    private static final String TOKEN_URL = "https://graph.qq.com/oauth2.0/token";

    /** QQ 互联 OpenID 端点。 */
    private static final String OPEN_ID_URL = "https://graph.qq.com/oauth2.0/me";

    private final OAuthHttpClient oauthHttpClient;

    public QqOAuthProviderClient(OAuthProviderProperties properties, OAuthHttpClient oauthHttpClient) {
        super(properties);
        this.oauthHttpClient = oauthHttpClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OAuthProvider provider() {
        return OAuthProvider.QQ;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supportsPkce() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String createAuthorizationUrl(OAuthAuthorizationRequest request) {
        OAuthProviderProperties.Provider configuration = requireProviderConfiguration();
        return createUrl(AUTHORIZATION_URL, builder -> builder
                .queryParam("response_type", "code")
                .queryParam("client_id", configuration.getClientId())
                .queryParam("redirect_uri", request.redirectUri())
                .queryParam("state", request.state()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OAuthPrincipal resolvePrincipal(String authorizationCode, String redirectUri, String pkceVerifier) {
        OAuthProviderProperties.Provider configuration = requireProviderConfiguration();
        String tokenUrl = createUrl(TOKEN_URL, builder -> builder
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", configuration.getClientId())
                .queryParam("client_secret", configuration.getClientSecret())
                .queryParam("code", authorizationCode)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("fmt", "json"));
        JsonNode token = oauthHttpClient.getJson(tokenUrl, headers -> {
        });
        String accessToken = requireText(token, "access_token");
        String openIdUrl = createUrl(OPEN_ID_URL, builder -> builder
                .queryParam("access_token", accessToken)
                .queryParam("fmt", "json"));
        JsonNode openId = oauthHttpClient.getJson(openIdUrl, headers -> {
        });
        return new OAuthPrincipal(requireText(openId, "openid"));
    }
}
