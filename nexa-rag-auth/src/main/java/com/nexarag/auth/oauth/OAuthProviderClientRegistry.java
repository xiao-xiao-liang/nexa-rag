package com.nexarag.auth.oauth;

import com.nexarag.auth.enums.OAuthProvider;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.oauth.client.OAuthProviderClient;
import com.nexarag.common.exception.ClientException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * OAuth 提供方客户端注册表，避免控制器按字符串分支处理平台协议。
 */
@Component
public class OAuthProviderClientRegistry {

    private final Map<OAuthProvider, OAuthProviderClient> clients;

    public OAuthProviderClientRegistry(List<OAuthProviderClient> providerClients) {
        Map<OAuthProvider, OAuthProviderClient> registeredClients = new EnumMap<>(OAuthProvider.class);
        for (OAuthProviderClient providerClient : providerClients) {
            OAuthProviderClient previous = registeredClients.put(providerClient.provider(), providerClient);
            if (previous != null) {
                throw new IllegalStateException("存在重复 OAuth 提供方客户端：" + providerClient.provider());
            }
        }
        this.clients = Map.copyOf(registeredClients);
    }

    /**
     * 获取指定提供方的协议客户端。
     *
     * @param provider 提供方
     * @return 对应协议客户端
     */
    public OAuthProviderClient getRequired(OAuthProvider provider) {
        OAuthProviderClient client = clients.get(provider);
        if (client == null) {
            throw new ClientException(AuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
        }
        return client;
    }
}
