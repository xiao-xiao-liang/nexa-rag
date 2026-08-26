package com.nexarag.auth.service;

import com.nexarag.auth.model.vo.ExternalIdentityVO;
import com.nexarag.auth.model.vo.OAuthAuthorizationVO;
import com.nexarag.auth.model.vo.OAuthCallbackVO;

import java.util.List;

/**
 * 第三方登录、绑定与解绑服务。
 */
public interface OAuthAuthenticationService {

    /**
     * 创建匿名第三方登录授权地址。
     *
     * @param providerCode 提供方 URL 编码
     * @param accountName 首次第三方登录注册时使用的账号名；已有绑定登录时可为空
     * @return 授权地址
     */
    OAuthAuthorizationVO startLogin(String providerCode, String accountName);

    /**
     * 完成第三方回调，执行登录、注册或绑定。
     *
     * @param providerCode 提供方 URL 编码
     * @param authorizationCode 回调授权码
     * @param state 回调 state
     * @param providerError 平台回调错误码
     * @return 回调完成结果
     */
    OAuthCallbackVO completeCallback(String providerCode, String authorizationCode, String state, String providerError);

    /**
     * 为当前最近验证过的会话创建第三方绑定授权地址。
     *
     * @param providerCode 提供方 URL 编码
     * @return 授权地址
     */
    OAuthAuthorizationVO startBinding(String providerCode);

    /**
     * 查询当前用户的已绑定第三方身份。
     *
     * @return 不包含主体标识的第三方身份列表
     */
    List<ExternalIdentityVO> listCurrentUserIdentities();

    /**
     * 精确解绑当前用户的一条指定第三方身份。
     *
     * @param providerCode 提供方 URL 编码
     * @param externalIdentityId 绑定记录 ID
     */
    void unbind(String providerCode, Long externalIdentityId);
}
