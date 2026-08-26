package com.nexarag.auth.oauth;

/**
 * 已由提供方验证且仅包含稳定主体标识的第三方身份。
 *
 * @param subject 提供方分配的不可变主体标识
 */
public record OAuthPrincipal(String subject) {
}
