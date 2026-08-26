package com.nexarag.auth.web;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.nexarag.auth.constants.AuthSessionConstants;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.model.vo.CsrfTokenVO;
import com.nexarag.common.exception.ClientException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 签发并校验 CSRF 挑战：登录后绑定 Sa-Token Token-Session，登录前绑定匿名 HTTP 会话。
 */
@Service
@Slf4j
public class CsrfTokenService {

    /** 匿名 HTTP 会话中保存 CSRF 挑战的键名。 */
    private static final String ANONYMOUS_CSRF_TOKEN = "nexa.auth.csrfToken";

    /** CSRF 随机字节长度。 */
    private static final int TOKEN_BYTES = 32;

    /** 安全随机数生成器。 */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 获取当前浏览器会话的 CSRF 挑战；不存在时创建一个新挑战。
     *
     * @return CSRF 挑战展示对象
     */
    public CsrfTokenVO getOrCreateToken() {
        String token = getOrCreateToken(currentRequest());
        return new CsrfTokenVO(token);
    }

    /**
     * 在建立新的 Sa-Token 登录态后轮换 CSRF 挑战，使登录前匿名挑战立即失效。
     */
    public void rotateForCurrentLogin() {
        if (!StpUtil.isLogin()) {
            throw new IllegalStateException("当前线程不存在 Sa-Token 登录态");
        }
        StpUtil.getTokenSession().set(AuthSessionConstants.CSRF_TOKEN, generateToken());
    }

    /**
     * 校验当前请求的自定义 CSRF Header。
     *
     * @param request 当前 HTTP 请求
     */
    public void validateCurrentRequestToken(HttpServletRequest request) {
        String expectedToken = findToken(request);
        String actualToken = request.getHeader(CsrfRequestValidator.CSRF_HEADER_NAME);
        if (expectedToken == null || actualToken == null || !constantTimeEquals(expectedToken, actualToken)) {
            log.warn("CSRF 令牌校验失败，uri={}, expectedTokenPresent={}, requestTokenPresent={}",
                    request.getRequestURI(), expectedToken != null, actualToken != null);
            throw ClientException.forbidden(AuthErrorCode.CSRF_TOKEN_VALIDATION_FAILED);
        }
    }

    /**
     * 获取或创建与当前身份状态匹配的挑战。
     */
    private String getOrCreateToken(HttpServletRequest request) {
        String token = findToken(request);
        if (token != null) {
            return token;
        }
        token = generateToken();
        storeToken(request, token);
        return token;
    }

    /**
     * 查询当前请求使用的挑战。
     */
    private String findToken(HttpServletRequest request) {
        if (StpUtil.isLogin()) {
            SaSession tokenSession = StpUtil.getTokenSession();
            Object token = tokenSession.get(AuthSessionConstants.CSRF_TOKEN);
            return token instanceof String value && !value.isBlank() ? value : null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object token = session.getAttribute(ANONYMOUS_CSRF_TOKEN);
        return token instanceof String value && !value.isBlank() ? value : null;
    }

    /**
     * 存储当前身份状态使用的挑战。
     */
    private void storeToken(HttpServletRequest request, String token) {
        if (StpUtil.isLogin()) {
            StpUtil.getTokenSession().set(AuthSessionConstants.CSRF_TOKEN, token);
            return;
        }
        request.getSession(true).setAttribute(ANONYMOUS_CSRF_TOKEN, token);
    }

    /**
     * 生成不可预测且适合 HTTP Header 传递的挑战值。
     */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 使用常量时间比较避免通过响应时间推断挑战内容。
     */
    private boolean constantTimeEquals(String expectedToken, String actualToken) {
        return java.security.MessageDigest.isEqual(expectedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actualToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 从当前 Web 请求上下文获取请求对象。
     */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new IllegalStateException("当前线程不存在 HTTP 请求上下文");
    }
}
