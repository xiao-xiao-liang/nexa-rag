package com.nexarag.auth.config;

import com.nexarag.auth.enums.OAuthProvider;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * 第三方 OAuth 提供方配置，Client Secret 仅通过部署环境注入。
 *
 * <p>各平台的授权端点属于协议契约，固定在对应客户端中；配置只承载部署差异，避免错误覆盖端点。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nexa.auth.oauth")
public class OAuthProviderProperties {

    /** OAuth 回调对外基址。 */
    private String publicBaseUrl;

    /** 各提供方独立配置。 */
    private Map<OAuthProvider, Provider> providers = new EnumMap<>(OAuthProvider.class);

    /**
     * 单一 OAuth 提供方的部署凭据。
     */
    @Getter
    @Setter
    public static class Provider {

        /** 平台分配的客户端或应用标识。 */
        private String clientId;

        /** 平台分配的客户端或应用密钥。 */
        private String clientSecret;
    }
}
