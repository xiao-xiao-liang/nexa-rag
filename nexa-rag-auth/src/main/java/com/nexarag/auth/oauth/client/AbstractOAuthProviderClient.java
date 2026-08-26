package com.nexarag.auth.oauth.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexarag.auth.config.OAuthProviderProperties;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.common.exception.ClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OAuth 提供方客户端的公共安全约束。
 */
abstract class AbstractOAuthProviderClient implements OAuthProviderClient {

    private final OAuthProviderProperties properties;

    protected AbstractOAuthProviderClient(OAuthProviderProperties properties) {
        this.properties = properties;
    }

    /**
     * 读取并校验当前提供方的部署配置。
     */
    protected OAuthProviderProperties.Provider requireProviderConfiguration() {
        OAuthProviderProperties.Provider provider = properties.getProviders().get(provider());
        if (provider == null
                || isBlank(provider.getClientId()) || isBlank(provider.getClientSecret())) {
            throw new ClientException(AuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
        }
        return provider;
    }

    /**
     * 根据固定协议端点安全构造浏览器重定向 URL。
     */
    protected String createUrl(String baseUrl, java.util.function.Consumer<UriComponentsBuilder> parameters) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);
        parameters.accept(builder);
        return builder.build().encode().toUriString();
    }

    /**
     * 从 JSON 节点中读取必填的字符串字段。
     */
    protected String requireText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText();
        if (isBlank(value)) {
            throw new ClientException(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        return value;
    }

    /**
     * 从路径中读取必填的字符串字段。
     */
    protected String requireTextAt(JsonNode node, String... fieldNames) {
        JsonNode current = node;
        for (String fieldName : fieldNames) {
            current = current.path(fieldName);
        }
        String value = current.asText();
        if (isBlank(value)) {
            throw new ClientException(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        return value;
    }

    /**
     * 检查第三方业务响应中的通用错误码。
     */
    protected void requireSuccessfulCode(JsonNode response) {
        JsonNode code = response.get("code");
        if (code != null && !code.canConvertToInt()) {
            throw new ClientException(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        if (code != null && code.asInt() != 0) {
            throw new ClientException(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
    }

    /**
     * 判断字符串是否为空白。
     */
    protected boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
