package com.nexarag.boot.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.enums.GlobalRoleCode;
import com.nexarag.auth.constants.AuthPermissionConstants;
import com.nexarag.auth.web.CsrfRequestValidator;
import com.nexarag.auth.service.DeviceSessionService;
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
                    SaRouter.match("/api/**")
                            .notMatch("/api/auth/login/account", "/api/auth/login/email-password",
                                    "/api/auth/login/email-code", "/api/auth/register", "/api/auth/email/send-code",
                                    "/api/auth/password/reset", "/api/auth/csrf-token",
                                    "/api/auth/oauth/*/start", "/api/auth/oauth/*/callback")
                            .check(r -> requireLogin());

                    // 2. 管理 API 按资源校验权限，避免一种权限隐式访问其他管理能力
                    SaRouter.match("/api/model/**")
                            .notMatch("/api/model/prompts/**")
                            .check(r -> requirePermission(AuthPermissionConstants.MODEL_MANAGE));
                    SaRouter.match("/api/model/prompts/**")
                            .check(r -> requirePermission(AuthPermissionConstants.PROMPT_MANAGE));
                    SaRouter.match("/api/crm/**")
                            .check(r -> requirePermission(AuthPermissionConstants.CRM_VIEW));
                }).isAnnotation(false))
                .addPathPatterns("/**")
                .excludePathPatterns("/error", "/favicon.ico");
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
                csrfRequestValidator.validate(request);
                return true;
            }
        }).addPathPatterns("/api/**");
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
                if (StpUtil.isLogin()) {
                    deviceSessionService.touchCurrentSession();
                }
                return true;
            }
        }).addPathPatterns("/api/**");
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
