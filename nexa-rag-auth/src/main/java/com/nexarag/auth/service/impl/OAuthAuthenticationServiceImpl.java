package com.nexarag.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.nexarag.auth.config.OAuthProviderProperties;
import com.nexarag.auth.enums.OAuthProvider;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.model.vo.OAuthAuthorizationVO;
import com.nexarag.auth.model.vo.OAuthCallbackVO;
import com.nexarag.auth.model.vo.ExternalIdentityVO;
import com.nexarag.auth.enums.OAuthAction;
import com.nexarag.auth.oauth.OAuthAuthorizationRequest;
import com.nexarag.auth.oauth.OAuthPrincipal;
import com.nexarag.auth.oauth.client.OAuthProviderClient;
import com.nexarag.auth.oauth.OAuthProviderClientRegistry;
import com.nexarag.auth.oauth.OAuthStateContext;
import com.nexarag.auth.oauth.OAuthStateService;
import com.nexarag.auth.service.AccountNamePolicy;
import com.nexarag.auth.service.OAuthAuthenticationService;
import com.nexarag.auth.service.OAuthIdentityService;
import com.nexarag.auth.service.RecentVerificationService;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

/**
 * 第三方 OAuth 编排实现：负责 state、PKCE、回调会话复验，不处理平台协议细节。
 */
@Service
@RequiredArgsConstructor
public class OAuthAuthenticationServiceImpl implements OAuthAuthenticationService {

    private final OAuthProviderProperties properties;
    private final OAuthProviderClientRegistry providerClientRegistry;
    private final OAuthStateService oauthStateService;
    private final OAuthIdentityService oauthIdentityService;
    private final RecentVerificationService recentVerificationService;
    private final AccountNamePolicy accountNamePolicy;

    /**
     * {@inheritDoc}
     */
    @Override
    public OAuthAuthorizationVO startLogin(String providerCode, String accountName) {
        OAuthProvider provider = OAuthProvider.fromCode(providerCode);
        OAuthProviderClient client = providerClientRegistry.getRequired(provider);
        String normalizedAccountName = normalizeOptionalAccountName(accountName);
        return createAuthorization(provider, client,
                new OAuthStateContext(provider, OAuthAction.LOGIN, null, null, normalizedAccountName, null, null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OAuthCallbackVO completeCallback(String providerCode, String authorizationCode, String state, String providerError) {
        OAuthProvider provider = OAuthProvider.fromCode(providerCode);
        OAuthStateContext context = oauthStateService.consume(state, provider);
        if (providerError != null && !providerError.isBlank()) {
            throw new ClientException(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new ClientException(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }

        // 1. 使用单次 state 关联的 PKCE verifier 换取并解析第三方不可变主体
        OAuthProviderClient client = providerClientRegistry.getRequired(provider);
        OAuthPrincipal principal = client.resolvePrincipal(authorizationCode, context.redirectUri(), context.pkceVerifier());

        // 2. 仅 state 明确标识的业务动作可执行，禁止由回调参数决定登录或绑定语义
        return switch (context.action()) {
            case LOGIN -> oauthIdentityService.loginOrRegister(provider, principal.subject(), principal.displayName(),
                    context.accountName());
            case BIND -> completeBinding(context, provider, principal);
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OAuthAuthorizationVO startBinding(String providerCode) {
        // 1. 绑定入口必须有当前 Sa-Token 登录态和最近验证授权
        Long userId = StpUtil.getLoginIdAsLong();
        recentVerificationService.requireCurrentSessionGrant();
        String tokenValue = StpUtil.getTokenValueNotCut();
        if (tokenValue == null || tokenValue.isBlank()) {
            throw ClientException.unauthorized(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }

        // 2. 原 Token 仅保留在 Redis state，回调时据此复验 SameSite=Strict 下未随请求传递的原会话
        OAuthProvider provider = OAuthProvider.fromCode(providerCode);
        OAuthProviderClient client = providerClientRegistry.getRequired(provider);
        return createAuthorization(provider, client,
                new OAuthStateContext(provider, OAuthAction.BIND, userId, tokenValue, null, null, null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ExternalIdentityVO> listCurrentUserIdentities() {
        return oauthIdentityService.listByUserId(StpUtil.getLoginIdAsLong());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unbind(String providerCode, Long externalIdentityId) {
        recentVerificationService.requireCurrentSessionGrant();
        oauthIdentityService.unbind(StpUtil.getLoginIdAsLong(), OAuthProvider.fromCode(providerCode), externalIdentityId);
    }

    /**
     * 创建 state、按平台能力附加 PKCE，并构造官方授权地址。
     */
    private OAuthAuthorizationVO createAuthorization(OAuthProvider provider, OAuthProviderClient client,
                                                      OAuthStateContext sourceContext) {
        String pkceVerifier = client.supportsPkce() ? oauthStateService.createPkceVerifier() : null;
        String pkceChallenge = pkceVerifier == null ? null : oauthStateService.createPkceChallenge(pkceVerifier);
        String redirectUri = callbackUrl(provider);
        OAuthStateContext context = new OAuthStateContext(provider, sourceContext.action(), sourceContext.bindingUserId(),
                sourceContext.bindingTokenValue(), sourceContext.accountName(), pkceVerifier, redirectUri);
        String state = oauthStateService.create(context);
        String authorizationUrl = client.createAuthorizationUrl(new OAuthAuthorizationRequest(state, redirectUri, pkceChallenge));
        return new OAuthAuthorizationVO(authorizationUrl);
    }

    /**
     * 完成绑定前复验发起 state 的登录态仍有效、归属未改变且仍拥有最近验证授权。
     */
    private OAuthCallbackVO completeBinding(OAuthStateContext context, OAuthProvider provider, OAuthPrincipal principal) {
        validateBindingSession(context);
        return oauthIdentityService.bind(context.bindingUserId(), provider, principal.subject());
    }

    /**
     * 使用 Sa-Token 按 state 中的原 Token 复验登录主体；不依赖跨站回调 Cookie。
     */
    private void validateBindingSession(OAuthStateContext context) {
        if (context.bindingUserId() == null || context.bindingTokenValue() == null || context.bindingTokenValue().isBlank()) {
            throw new ClientException(AuthErrorCode.OAUTH_STATE_INVALID);
        }
        Object loginId;
        try {
            loginId = StpUtil.getLoginIdByToken(context.bindingTokenValue());
        } catch (RuntimeException exception) {
            throw new ClientException(AuthErrorCode.OAUTH_STATE_INVALID);
        }
        if (loginId == null || !context.bindingUserId().toString().equals(String.valueOf(loginId))) {
            throw new ClientException(AuthErrorCode.OAUTH_STATE_INVALID);
        }
        recentVerificationService.requireGrantForToken(context.bindingTokenValue());
    }

    /**
     * 校验部署回调基址并按固定路由生成提供方专属回调地址。
     */
    private String callbackUrl(OAuthProvider provider) {
        String publicBaseUrl = properties.getPublicBaseUrl();
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new ClientException(AuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
        }
        try {
            String normalizedBaseUrl = publicBaseUrl.trim();
            URI uri = URI.create(normalizedBaseUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new ClientException(AuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
            }
            return normalizedBaseUrl.replaceAll("/+$", "") + "/api/auth/oauth/" + provider.getCode() + "/callback";
        } catch (IllegalArgumentException exception) {
            throw new ClientException(AuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
        }
    }

    /**
     * 仅在调用方实际填写账号名时校验规则；保留为空以支持已绑定第三方账号直接登录。
     */
    private String normalizeOptionalAccountName(String accountName) {
        if (accountName == null || accountName.isBlank()) {
            return null;
        }
        accountNamePolicy.normalizeAndValidate(accountName);
        return accountName.trim();
    }
}
