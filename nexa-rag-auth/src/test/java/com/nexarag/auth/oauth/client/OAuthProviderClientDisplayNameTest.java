package com.nexarag.auth.oauth.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.auth.config.OAuthProviderProperties;
import com.nexarag.auth.oauth.OAuthPrincipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OAuth 提供方展示名称解析测试。
 */
class OAuthProviderClientDisplayNameTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 验证 GitHub 使用稳定 ID 绑定身份，并读取 login 作为展示名称。
     */
    @Test
    void shouldResolveGitHubLoginAsDisplayName() throws Exception {
        OAuthHttpClient httpClient = mock(OAuthHttpClient.class);
        when(httpClient.postForm(anyString(), anyMap(), any())).thenReturn(json("{\"access_token\":\"token\"}"));
        when(httpClient.getJson(anyString(), any())).thenReturn(json("{\"id\":123,\"login\":\"octocat\"}"));

        OAuthPrincipal principal = new GitHubOAuthProviderClient(properties(), httpClient)
                .resolvePrincipal("code", "https://example.com/callback", "verifier");

        assertThat(principal.subject()).isEqualTo("123");
        assertThat(principal.displayName()).isEqualTo("octocat");
    }

    /**
     * 验证 Google 读取 profile scope 返回的 name。
     */
    @Test
    void shouldResolveGoogleNameAsDisplayName() throws Exception {
        OAuthHttpClient httpClient = mock(OAuthHttpClient.class);
        when(httpClient.postForm(anyString(), anyMap(), any())).thenReturn(json("{\"access_token\":\"token\"}"));
        when(httpClient.getJson(anyString(), any())).thenReturn(json("{\"sub\":\"google-sub\",\"name\":\"Google User\"}"));

        OAuthPrincipal principal = new GoogleOAuthProviderClient(properties(), httpClient)
                .resolvePrincipal("code", "https://example.com/callback", "verifier");

        assertThat(principal.subject()).isEqualTo("google-sub");
        assertThat(principal.displayName()).isEqualTo("Google User");
    }

    /**
     * 验证飞书读取用户资料中的 name。
     */
    @Test
    void shouldResolveFeishuNameAsDisplayName() throws Exception {
        OAuthHttpClient httpClient = mock(OAuthHttpClient.class);
        when(httpClient.postJson(anyString(), anyMap(), any()))
                .thenReturn(json("{\"code\":0,\"app_access_token\":\"app-token\"}"),
                        json("{\"code\":0,\"data\":{\"access_token\":\"user-token\"}}"));
        when(httpClient.getJson(anyString(), any()))
                .thenReturn(json("{\"code\":0,\"data\":{\"open_id\":\"open-id\",\"name\":\"飞书用户\"}}"));

        OAuthPrincipal principal = new FeishuOAuthProviderClient(properties(), httpClient)
                .resolvePrincipal("code", "https://example.com/callback", null);

        assertThat(principal.subject()).isEqualTo("open-id");
        assertThat(principal.displayName()).isEqualTo("飞书用户");
    }

    /**
     * 验证 QQ 查询昵称并保留 OpenID 作为稳定主体。
     */
    @Test
    void shouldResolveQqNicknameAsDisplayName() throws Exception {
        OAuthHttpClient httpClient = mock(OAuthHttpClient.class);
        when(httpClient.getJson(anyString(), any())).thenReturn(
                json("{\"access_token\":\"token\"}"),
                json("{\"openid\":\"qq-open-id\"}"),
                json("{\"nickname\":\"QQ 用户\"}"));

        OAuthPrincipal principal = new QqOAuthProviderClient(properties(), httpClient)
                .resolvePrincipal("code", "https://example.com/callback", null);

        assertThat(principal.subject()).isEqualTo("qq-open-id");
        assertThat(principal.displayName()).isEqualTo("QQ 用户");
    }

    /**
     * 构造用于客户端单测的完整 OAuth 提供方配置。
     */
    private OAuthProviderProperties properties() {
        OAuthProviderProperties properties = new OAuthProviderProperties();
        OAuthProviderProperties.Provider provider = new OAuthProviderProperties.Provider();
        provider.setClientId("client-id");
        provider.setClientSecret("client-secret");
        for (com.nexarag.auth.enums.OAuthProvider value : com.nexarag.auth.enums.OAuthProvider.values()) {
            properties.getProviders().put(value, provider);
        }
        return properties;
    }

    /**
     * 解析测试构造的第三方 JSON 响应。
     */
    private JsonNode json(String text) throws Exception {
        return OBJECT_MAPPER.readTree(text);
    }
}
