package com.nexarag.auth.oauth.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexarag.auth.config.OAuthProviderProperties;
import com.nexarag.auth.enums.OAuthProvider;
import com.nexarag.auth.oauth.OAuthAuthorizationRequest;
import com.nexarag.auth.oauth.OAuthPrincipal;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * GitHub 用户授权码客户端。
 *
 * <p>稳定主体取 {@code /user} 响应的数值 {@code id}，不使用可改名的 {@code login}。</p>
 */
@Component
public class GitHubOAuthProviderClient extends AbstractOAuthProviderClient {

    /** GitHub 授权端点。 */
    private static final String AUTHORIZATION_URL = "https://github.com/login/oauth/authorize";

    /** GitHub Token 端点。 */
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";

    /** GitHub 当前用户端点。 */
    private static final String USER_URL = "https://api.github.com/user";

    private final OAuthHttpClient oauthHttpClient;

    public GitHubOAuthProviderClient(OAuthProviderProperties properties, OAuthHttpClient oauthHttpClient) {
        super(properties);
        this.oauthHttpClient = oauthHttpClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GITHUB;
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
                "code_verifier", pkceVerifier), headers -> {
                });
        String accessToken = requireText(token, "access_token");
        JsonNode user = oauthHttpClient.getJson(USER_URL, headers -> headers.setBearerAuth(accessToken));
        return new OAuthPrincipal(requireText(user, "id"));
    }
}
