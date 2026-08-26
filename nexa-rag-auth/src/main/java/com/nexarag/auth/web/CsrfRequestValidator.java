package com.nexarag.auth.web;

import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.common.exception.ClientException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * 校验 API 状态变更请求的 CSRF Header、Origin 和 Fetch Metadata。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CsrfRequestValidator {

    /** 前端回传 CSRF 挑战的请求头名称。 */
    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";

    /** 浏览器跨站请求上下文请求头。 */
    private static final String SEC_FETCH_SITE_HEADER = "Sec-Fetch-Site";

    /** 允许的 Fetch Metadata 同站值。 */
    private static final Set<String> ALLOWED_FETCH_SITES = Set.of("same-origin", "same-site");

    /** 不会改变服务端状态的 HTTP 方法。 */
    private static final Set<String> SAFE_HTTP_METHODS = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(),
            HttpMethod.OPTIONS.name(), HttpMethod.TRACE.name());

    private final CsrfTokenService csrfTokenService;

    @Value("${nexa.auth.web.allowed-origin:}")
    private String allowedOrigin;

    /**
     * 仅校验 API 的状态变更请求，安全方法和预检请求直接放行。
     *
     * @param request 当前 HTTP 请求
     */
    public void validate(HttpServletRequest request) {
        if (!isStateChangingRequest(request)) {
            return;
        }
        validateOrigin(request);
        validateFetchMetadata(request);
        csrfTokenService.validateCurrentRequestToken(request);
    }

    /**
     * 判断请求方法是否会改变服务端状态。
     */
    private boolean isStateChangingRequest(HttpServletRequest request) {
        return !SAFE_HTTP_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT));
    }

    /**
     * 严格校验 Origin 必须与部署配置的浏览器源一致，禁止直接信任客户端转发头。
     */
    private void validateOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || allowedOrigin == null || allowedOrigin.isBlank() || !origin.equals(allowedOrigin)) {
            log.warn("请求来源校验失败，uri={}, originPresent={}, allowedOriginConfigured={}, originMatched={}",
                    request.getRequestURI(), origin != null, allowedOrigin != null && !allowedOrigin.isBlank(),
                    origin != null && origin.equals(allowedOrigin));
            throw ClientException.forbidden(AuthErrorCode.REQUEST_ORIGIN_VALIDATION_FAILED);
        }
    }

    /**
     * 拒绝浏览器明确标注为跨站的请求；旧客户端缺少该头时仍由 Origin 兜底。
     */
    private void validateFetchMetadata(HttpServletRequest request) {
        String fetchSite = request.getHeader(SEC_FETCH_SITE_HEADER);
        if (fetchSite != null && !ALLOWED_FETCH_SITES.contains(fetchSite.toLowerCase(Locale.ROOT))) {
            log.warn("浏览器请求上下文校验失败，uri={}, fetchSite={}", request.getRequestURI(), fetchSite);
            throw ClientException.forbidden(AuthErrorCode.FETCH_METADATA_VALIDATION_FAILED);
        }
    }

}
