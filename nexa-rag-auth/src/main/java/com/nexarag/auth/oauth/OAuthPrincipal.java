package com.nexarag.auth.oauth;

/**
 * 已由提供方验证的第三方身份。
 *
 * @param subject 提供方分配的不可变主体标识
 * @param displayName 提供方返回的原始展示名称，可为空
 */
public record OAuthPrincipal(String subject, String displayName) {
}
