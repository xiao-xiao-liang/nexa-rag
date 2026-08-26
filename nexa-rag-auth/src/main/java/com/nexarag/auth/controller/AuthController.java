package com.nexarag.auth.controller;

import com.nexarag.auth.model.dto.AccountPasswordLoginDTO;
import com.nexarag.auth.model.dto.EmailCodeLoginDTO;
import com.nexarag.auth.model.dto.EmailCodeSendDTO;
import com.nexarag.auth.model.dto.EmailPasswordLoginDTO;
import com.nexarag.auth.model.dto.PasswordResetDTO;
import com.nexarag.auth.model.dto.RegisterAccountDTO;
import com.nexarag.auth.model.vo.CsrfTokenVO;
import com.nexarag.auth.model.vo.EmailChallengeVO;
import com.nexarag.auth.model.vo.LoginSessionVO;
import com.nexarag.auth.model.vo.OAuthAuthorizationVO;
import com.nexarag.auth.model.vo.OAuthCallbackVO;
import com.nexarag.auth.service.AuthenticationService;
import com.nexarag.auth.service.CurrentUserProfileService;
import com.nexarag.auth.service.OAuthAuthenticationService;
import com.nexarag.auth.service.PasswordResetService;
import com.nexarag.auth.service.RegistrationService;
import com.nexarag.auth.web.CsrfTokenService;
import com.nexarag.auth.context.UserContext;
import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 匿名认证入口控制器，仅负责参数接收、服务调用和统一响应包装。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;
    private final OAuthAuthenticationService oauthAuthenticationService;
    private final CsrfTokenService csrfTokenService;
    private final CurrentUserProfileService currentUserProfileService;
    private final com.nexarag.auth.service.DeviceSessionService deviceSessionService;

    /**
     * 获取当前服务端已验证登录态对应的用户资料。
     *
     * @return 当前用户的角色和权限快照
     */
    @GetMapping("/me")
    public Result<LoginSessionVO> getCurrentUserProfile() {
        // 1. 读取 Sa-Token 已验证的当前用户和租户，未登录请求由全局路由策略拦截
        var currentUser = UserContext.getCurrUser();

        // 2. 返回服务端权威的角色和权限，前端不得自行推断管理员身份
        return Results.success(currentUserProfileService.getProfile(Long.valueOf(currentUser.userId()), currentUser.tenantId()));
    }

    /**
     * 退出当前设备会话登录态。
     *
     * @return 无数据成功响应
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        deviceSessionService.logoutCurrentSession();
        return Results.success();
    }

    /**
     * 获取当前浏览器状态变更请求需要携带的 CSRF 挑战。
     *
     * @return 当前匿名会话或 Sa-Token 登录态绑定的 CSRF 挑战
     */
    @GetMapping("/csrf-token")
    public Result<CsrfTokenVO> getCsrfToken() {
        return Results.success(csrfTokenService.getOrCreateToken());
    }

    /**
     * 发送匿名认证流程使用的邮箱验证码。
     *
     * @param sendDTO 验证码发送请求
     * @return 新建验证码挑战摘要
     */
    @PostMapping("/email/send-code")
    public Result<EmailChallengeVO> sendEmailCode(@RequestBody(required = false) @Valid EmailCodeSendDTO sendDTO) {
        return Results.success(authenticationService.sendAnonymousEmailCode(sendDTO));
    }

    /**
     * 使用首个已验证邮箱注册无密码账号并自动登录。
     *
     * @param registerDTO 注册请求
     * @return 注册成功后的当前用户和默认租户
     */
    @PostMapping("/register")
    public Result<LoginSessionVO> register(@RequestBody(required = false) @Valid RegisterAccountDTO registerDTO) {
        return Results.success(registrationService.register(registerDTO));
    }

    /**
     * 使用账号名和密码登录系统。
     *
     * @param loginDTO 账号密码登录请求
     * @return 登录成功后的当前用户和租户
     */
    @PostMapping("/login/account")
    public Result<LoginSessionVO> loginByAccountPassword(@RequestBody(required = false) @Valid AccountPasswordLoginDTO loginDTO) {
        return Results.success(authenticationService.loginByAccountPassword(loginDTO));
    }

    /**
     * 使用当前绑定邮箱和密码登录系统。
     *
     * @param loginDTO 邮箱密码登录请求
     * @return 登录成功后的当前用户和租户
     */
    @PostMapping("/login/email-password")
    public Result<LoginSessionVO> loginByEmailPassword(@RequestBody(required = false) @Valid EmailPasswordLoginDTO loginDTO) {
        return Results.success(authenticationService.loginByEmailPassword(loginDTO));
    }

    /**
     * 使用当前绑定邮箱和验证码登录系统。
     *
     * @param loginDTO 邮箱验证码登录请求
     * @return 登录成功后的当前用户和租户
     */
    @PostMapping("/login/email-code")
    public Result<LoginSessionVO> loginByEmailCode(@RequestBody(required = false) @Valid EmailCodeLoginDTO loginDTO) {
        return Results.success(authenticationService.loginByEmailCode(loginDTO));
    }

    /**
     * 使用邮箱验证码重置本地密码，并撤销该用户全部历史登录态。
     *
     * @param resetDTO 密码重置请求
     * @return 无数据成功响应；调用方需使用新密码或验证码重新登录
     */
    @PostMapping("/password/reset")
    public Result<Void> resetPassword(@RequestBody(required = false) @Valid PasswordResetDTO resetDTO) {
        passwordResetService.resetPassword(resetDTO);
        return Results.success();
    }

    /**
     * 创建第三方登录授权地址；首次第三方登录时，调用方应同时提供符合规范的账号名。
     *
     * @param provider 第三方提供方编码
     * @param accountName 首次第三方登录注册使用的账号名
     * @return 前端应跳转到的授权地址
     */
    @GetMapping("/oauth/{provider}/start")
    public Result<OAuthAuthorizationVO> startOAuthLogin(@PathVariable String provider,
                                                         @RequestParam(required = false) String accountName) {
        return Results.success(oauthAuthenticationService.startLogin(provider, accountName));
    }

    /**
     * 接收第三方平台授权回调，完成登录、首次注册或绑定。
     *
     * @param provider 第三方提供方编码
     * @param code 平台回传的一次性授权码
     * @param state 平台原样回传的一次性 state
     * @param error 平台回传的授权失败码
     * @return OAuth 回调处理结果
     */
    @GetMapping("/oauth/{provider}/callback")
    public Result<OAuthCallbackVO> completeOAuthCallback(@PathVariable String provider,
                                                          @RequestParam(required = false) String code,
                                                          @RequestParam(required = false) String state,
                                                          @RequestParam(required = false) String error) {
        return Results.success(oauthAuthenticationService.completeCallback(provider, code, state, error));
    }
}
