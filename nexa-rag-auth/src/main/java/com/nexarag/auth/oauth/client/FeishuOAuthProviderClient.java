package com.nexarag.auth.oauth.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexarag.auth.config.OAuthProviderProperties;
import com.nexarag.auth.enums.OAuthProvider;
import com.nexarag.auth.oauth.OAuthAuthorizationRequest;
import com.nexarag.auth.oauth.OAuthPrincipal;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 飞书网页授权客户端。
 *
 * <p>飞书用户授权码需要先换取短期应用 access token，再换取用户 access token 并查询用户信息。
 * 本系统只使用当前应用下稳定的 {@code open_id}，不保存任意飞书 token。</p>
 */
@Component
public class FeishuOAuthProviderClient extends AbstractOAuthProviderClient {

    /** 飞书网页授权端点。 */
    private static final String AUTHORIZATION_URL = "https://open.feishu.cn/open-apis/authen/v1/authorize";

    /** 企业自建应用 App Access Token 端点。 */
    private static final String APP_ACCESS_TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal";

    /** 使用用户授权码获取 User Access Token 的端点。 */
    private static final String USER_ACCESS_TOKEN_URL = "https://open.feishu.cn/open-apis/authen/v1/access_token";

    /** 飞书用户信息端点。 */
    private static final String USER_INFO_URL = "https://open.feishu.cn/open-apis/authen/v1/user_info";

    private final OAuthHttpClient oauthHttpClient;

    public FeishuOAuthProviderClient(OAuthProviderProperties properties, OAuthHttpClient oauthHttpClient) {
        super(properties);
        this.oauthHttpClient = oauthHttpClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OAuthProvider provider() {
        return OAuthProvider.FEISHU;
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
                .queryParam("app_id", configuration.getClientId())
                .queryParam("redirect_uri", request.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("state", request.state()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OAuthPrincipal resolvePrincipal(String authorizationCode, String redirectUri, String pkceVerifier) {
        OAuthProviderProperties.Provider configuration = requireProviderConfiguration();

        // 1. 应用凭据只用于本次回调换取飞书 app access token，不缓存也不对外返回
        JsonNode applicationToken = oauthHttpClient.postJson(APP_ACCESS_TOKEN_URL, Map.of(
                "app_id", configuration.getClientId(),
                "app_secret", configuration.getClientSecret()), headers -> {
                });
        requireSuccessfulCode(applicationToken);
        String appAccessToken = requireText(applicationToken, "app_access_token");

        // 2. code 交换得到用户 token，随后立即只用于读取 stable open_id
        JsonNode userToken = oauthHttpClient.postJson(USER_ACCESS_TOKEN_URL, Map.of(
                "grant_type", "authorization_code",
                "code", authorizationCode), headers -> headers.setBearerAuth(appAccessToken));
        requireSuccessfulCode(userToken);
        String userAccessToken = requireTextAt(userToken, "data", "access_token");
        JsonNode userInfo = oauthHttpClient.getJson(USER_INFO_URL, headers -> headers.setBearerAuth(userAccessToken));
        requireSuccessfulCode(userInfo);
        return new OAuthPrincipal(requireTextAt(userInfo, "data", "open_id"),
                optionalTextAt(userInfo, "data", "name"));
    }
}
