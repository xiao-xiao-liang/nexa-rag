package com.nexarag.auth.service;

import com.nexarag.auth.enums.OAuthProvider;
import com.nexarag.auth.model.vo.ExternalIdentityVO;
import com.nexarag.auth.model.vo.OAuthCallbackVO;

import java.util.List;

/**
 * OAuth 稳定主体与本地认证用户的事务性绑定服务。
 */
public interface OAuthIdentityService {

    /**
     * 用已验证的第三方主体登录；不存在绑定时按账号名创建用户和身份绑定。
     */
    OAuthCallbackVO loginOrRegister(OAuthProvider provider, String providerSubject, String accountName);

    /**
     * 将第三方主体绑定到已由 OAuth state 复验的本地用户。
     */
    OAuthCallbackVO bind(Long userId, OAuthProvider provider, String providerSubject);

    /**
     * 查询用户已绑定的第三方身份，不泄露稳定主体。
     */
    List<ExternalIdentityVO> listByUserId(Long userId);

    /**
     * 精确解绑指定第三方身份，且不允许移除最后一种登录凭据。
     */
    void unbind(Long userId, OAuthProvider provider, Long externalIdentityId);
}
