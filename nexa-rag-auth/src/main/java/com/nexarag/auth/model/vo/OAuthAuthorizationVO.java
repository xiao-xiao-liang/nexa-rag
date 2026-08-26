package com.nexarag.auth.model.vo;

/**
 * OAuth 授权跳转展示对象。
 *
 * @param authorizationUrl 前端应打开或重定向到的第三方授权地址
 */
public record OAuthAuthorizationVO(String authorizationUrl) {
}
