package com.nexarag.auth.model.vo;

/**
 * OAuth 回调完成展示对象。
 *
 * @param action 已完成的本地业务动作：LOGIN 或 BIND
 * @param loginSession 当前用户和默认租户摘要；绑定流程不新建登录态
 */
public record OAuthCallbackVO(String action, LoginSessionVO loginSession) {
}
