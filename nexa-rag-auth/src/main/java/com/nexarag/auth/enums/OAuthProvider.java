package com.nexarag.auth.enums;

import com.nexarag.common.exception.ClientException;
import lombok.Getter;

import java.util.Arrays;

/**
 * 支持的第三方 OAuth 身份提供方。
 */
@Getter
public enum OAuthProvider {

    /** QQ 互联。 */
    QQ("qq"),

    /** 飞书开放平台。 */
    FEISHU("feishu"),

    /** Google OpenID Connect。 */
    GOOGLE("google"),

    /** GitHub OAuth 或 GitHub App 的用户授权流。 */
    GITHUB("github");

    /** 用于 URL 路径和持久化的稳定小写编码。
     * -- GETTER --
     *  获取稳定小写编码。
     *
     * @return 提供方编码
     */
    private final String code;

    OAuthProvider(String code) {
        this.code = code;
    }

    /**
     * 解析外部 URL 中的提供方编码。
     *
     * @param code 路径中的提供方编码
     * @return 已支持的提供方
     */
    public static OAuthProvider fromCode(String code) {
        return Arrays.stream(values())
                .filter(provider -> provider.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new ClientException(AuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE));
    }
}
