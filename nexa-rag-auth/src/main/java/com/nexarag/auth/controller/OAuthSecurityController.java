package com.nexarag.auth.controller;

import com.nexarag.auth.model.vo.ExternalIdentityVO;
import com.nexarag.auth.model.vo.OAuthAuthorizationVO;
import com.nexarag.auth.service.OAuthAuthenticationService;
import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 已登录用户的第三方身份敏感操作入口。
 */
@RestController
@RequestMapping("/api/auth/security/oauth")
@RequiredArgsConstructor
public class OAuthSecurityController {

    private final OAuthAuthenticationService oauthAuthenticationService;

    /**
     * 为当前最近验证过的会话创建第三方账号绑定授权地址。
     *
     * @param provider 第三方提供方编码
     * @return 前端应跳转到的授权地址
     */
    @PostMapping("/{provider}/binding/start")
    public Result<OAuthAuthorizationVO> startBinding(@PathVariable String provider) {
        return Results.success(oauthAuthenticationService.startBinding(provider));
    }

    /**
     * 查询当前用户已绑定的第三方账号，稳定主体不会返回给前端。
     *
     * @return 当前用户的第三方身份列表
     */
    @GetMapping("/identities")
    public Result<List<ExternalIdentityVO>> listIdentities() {
        return Results.success(oauthAuthenticationService.listCurrentUserIdentities());
    }

    /**
     * 精确解绑当前用户的一条第三方账号；服务层会拒绝移除最后一种登录凭据。
     *
     * @param provider 第三方提供方编码
     * @param externalIdentityId 绑定记录 ID
     * @return 无数据成功响应
     */
    @DeleteMapping("/{provider}/{externalIdentityId}")
    public Result<Void> unbind(@PathVariable String provider, @PathVariable Long externalIdentityId) {
        oauthAuthenticationService.unbind(provider, externalIdentityId);
        return Results.success();
    }
}
