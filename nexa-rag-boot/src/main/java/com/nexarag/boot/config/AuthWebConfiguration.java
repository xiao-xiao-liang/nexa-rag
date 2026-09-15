package com.nexarag.boot.config;

import static com.nexarag.boot.constants.AuthApiPathConstant.ALL;
import static com.nexarag.boot.constants.AuthApiPathConstant.API_ALL;
import static com.nexarag.boot.constants.AuthApiPathConstant.AUTH_ACCOUNT_LOGIN;
import static com.nexarag.boot.constants.AuthApiPathConstant.AUTH_CSRF_TOKEN;
import static com.nexarag.boot.constants.AuthApiPathConstant.AUTH_EMAIL_CODE_LOGIN;
import static com.nexarag.boot.constants.AuthApiPathConstant.AUTH_EMAIL_PASSWORD_LOGIN;
import static com.nexarag.boot.constants.AuthApiPathConstant.AUTH_OAUTH_CALLBACK;
import static com.nexarag.boot.constants.AuthApiPathConstant.AUTH_OAUTH_START;
import static com.nexarag.boot.constants.AuthApiPathConstant.AUTH_PASSWORD_RESET;
import static com.nexarag.boot.constants.AuthApiPathConstant.AUTH_REGISTER;
import static com.nexarag.boot.constants.AuthApiPathConstant.AUTH_SEND_EMAIL_CODE;
import static com.nexarag.boot.constants.AuthApiPathConstant.CRM_ALL;
import static com.nexarag.boot.constants.AuthApiPathConstant.ERROR;
import static com.nexarag.boot.constants.AuthApiPathConstant.FAVICON;
import static com.nexarag.boot.constants.AuthApiPathConstant.MODEL_ALL;
import static com.nexarag.boot.constants.AuthApiPathConstant.MODEL_OBSERVABILITY_ALL;
import static com.nexarag.boot.constants.AuthApiPathConstant.MODEL_PROMPT_ALL;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.nexarag.auth.constants.AuthPermissionConstants;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.enums.GlobalRoleCode;
import com.nexarag.auth.service.DeviceSessionService;
import com.nexarag.auth.web.CsrfRequestValidator;
import com.nexarag.common.exception.ClientException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册认证模块的 Sa-Token Web 路由策略。
 */
@Configuration
@RequiredArgsConstructor
public class AuthWebConfiguration implements WebMvcConfigurer {

    private final CsrfRequestValidator csrfRequestValidator;
    private final DeviceSessionService deviceSessionService;

    /**
     * 注册 Sa-Token 原生路由拦截器，默认拒绝未登录 API 请求并保护模型管理接口。
     *
     * @param registry Spring MVC 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
                    // 1. 除精确认证入口外，所有 API 必须拥有 Sa-Token 登录态
                    SaRouter.match(API_ALL)
                            .notMatch(AUTH_ACCOUNT_LOGIN, AUTH_EMAIL_PASSWORD_LOGIN, AUTH_EMAIL_CODE_LOGIN, AUTH_REGISTER,
                                    AUTH_SEND_EMAIL_CODE, AUTH_PASSWORD_RESET, AUTH_CSRF_TOKEN, AUTH_OAUTH_START,
                                    AUTH_OAUTH_CALLBACK)
                            .check(r -> requireLogin());

                    // 2. 管理 API 按资源校验权限，避免一种权限隐式访问其他管理能力
                    SaRouter.match(MODEL_ALL)
                            .notMatch(MODEL_PROMPT_ALL)
                            .check(r -> requirePermission(AuthPermissionConstants.MODEL_MANAGE));
                    SaRouter.match(MODEL_PROMPT_ALL)
                            .check(r -> requirePermission(AuthPermissionConstants.PROMPT_MANAGE));
                    SaRouter.match(MODEL_OBSERVABILITY_ALL)
                            .check(r -> requirePermission(AuthPermissionConstants.MODEL_MANAGE));
                    SaRouter.match(CRM_ALL)
                            .check(r -> requirePermission(AuthPermissionConstants.CRM_VIEW));
                }).isAnnotation(false))
                .addPathPatterns(ALL)
                .excludePathPatterns(ERROR, FAVICON);
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
                csrfRequestValidator.validate(request);
                return true;
            }
        }).addPathPatterns(API_ALL);
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
                if (StpUtil.isLogin()) {
                    deviceSessionService.touchCurrentSession();
                }
                return true;
            }
        }).addPathPatterns(API_ALL);
    }

    /**
     * 将 Sa-Token 未登录状态转换为项目统一的 401 响应。
     */
    private void requireLogin() {
        if (!StpUtil.isLogin()) {
            throw ClientException.unauthorized(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    /**
     * 将 Sa-Token 无权限状态转换为项目统一的 403 响应。
     */
    private void requirePermission(String permission) {
        if (!StpUtil.hasRole(GlobalRoleCode.ADMIN.name()) && !StpUtil.hasPermission(permission)) {
            throw ClientException.forbidden(AuthErrorCode.ACCESS_DENIED);
        }
    }
}
