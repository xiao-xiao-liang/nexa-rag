package com.nexarag.auth.oauth.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexarag.auth.config.OAuthProviderProperties;
import com.nexarag.auth.enums.OAuthProvider;
import com.nexarag.auth.oauth.OAuthAuthorizationRequest;
import com.nexarag.auth.oauth.OAuthPrincipal;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Google OpenID Connect 授权码客户端。
 *
 * <p>稳定主体取 UserInfo 的 {@code sub}；该值由 Google 声明为全局唯一且永不复用。</p>
 */
@Component
public class GoogleOAuthProviderClient extends AbstractOAuthProviderClient {

    /** Google OIDC 授权端点。 */
    private static final String AUTHORIZATION_URL = "https://accounts.google.com/o/oauth2/v2/auth";

    /** Google OAuth Token 端点。 */
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    /** Google OIDC UserInfo 端点。 */
    private static final String USER_INFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    private final OAuthHttpClient oauthHttpClient;

    public GoogleOAuthProviderClient(OAuthProviderProperties properties, OAuthHttpClient oauthHttpClient) {
        super(properties);
        this.oauthHttpClient = oauthHttpClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GOOGLE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supportsPkce() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String createAuthorizationUrl(OAuthAuthorizationRequest request) {
        OAuthProviderProperties.Provider configuration = requireProviderConfiguration();
        return createUrl(AUTHORIZATION_URL, builder -> builder
                .queryParam("client_id", configuration.getClientId())
                .queryParam("redirect_uri", request.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid profile")
                .queryParam("state", request.state())
                .queryParam("code_challenge", request.pkceChallenge())
                .queryParam("code_challenge_method", "S256"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OAuthPrincipal resolvePrincipal(String authorizationCode, String redirectUri, String pkceVerifier) {
        OAuthProviderProperties.Provider configuration = requireProviderConfiguration();
        JsonNode token = oauthHttpClient.postForm(TOKEN_URL, Map.of(
                "client_id", configuration.getClientId(),
                "client_secret", configuration.getClientSecret(),
                "code", authorizationCode,
                "redirect_uri", redirectUri,
                "grant_type", "authorization_code",
                "code_verifier", pkceVerifier), headers -> {
                });
        String accessToken = requireText(token, "access_token");
        JsonNode userInfo = oauthHttpClient.getJson(USER_INFO_URL,
                headers -> headers.setBearerAuth(accessToken));
        return new OAuthPrincipal(requireText(userInfo, "sub"), optionalText(userInfo, "name"));
    }
}
