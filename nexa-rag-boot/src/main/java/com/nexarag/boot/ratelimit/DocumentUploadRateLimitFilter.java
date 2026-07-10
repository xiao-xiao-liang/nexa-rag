package com.nexarag.boot.ratelimit;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.ratelimit.DistributedPermitLimiter;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 文档上传限流过滤器，负责在 multipart 解析前限制上传请求并发数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DocumentUploadRateLimitFilter extends OncePerRequestFilter {

    private static final String UPLOAD_METHOD = "POST";
    private static final String UPLOAD_PATH = "/api/documents/upload";
    private static final String APPLICATION_JSON_UTF8 = "application/json;charset=UTF-8";
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final DocumentUploadRateLimitProperties properties;
    private final DistributedPermitLimiter permitLimiter;

    /**
     * 初始化文档上传限流信号量。
     */
    @PostConstruct
    public void initialize() {
        if (!properties.isEnabled()) {
            log.info("文档上传限流未启用");
            return;
        }

        // 1. 初始化跨实例上传许可数量
        permitLimiter.initialize(properties.getSemaphoreName(), properties.getMaxConcurrent());
        log.info("文档上传限流初始化完成，semaphoreName={}，maxConcurrent={}",
                properties.getSemaphoreName(), properties.getMaxConcurrent());
    }

    /**
     * 对文档上传请求执行并发限流。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param chain    过滤器链
     * @throws ServletException Servlet 执行异常
     * @throws IOException      响应写入异常
     */
    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain chain) throws ServletException, IOException {
        if (!properties.isEnabled() || !isUploadRequest(request)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<String> permitId = Optional.empty();
        try {
            // 1. 上传入口先尝试获取分布式许可，避免 multipart 文件同时涌入
            permitId = permitLimiter.acquire(properties.getSemaphoreName(),
                    properties.getMaxWaitSeconds(), properties.getLeaseSeconds());
            if (permitId.isEmpty()) {
                writeTooManyRequests(response);
                return;
            }

            // 2. 获取许可后继续执行后续 multipart 解析和业务处理
            chain.doFilter(request, response);
        } catch (ServiceException exception) {
            log.error("获取文档上传许可失败，uri={}", request.getRequestURI(), exception);
            throw exception;
        } finally {
            // 3. 请求完成后释放上传许可
            permitId.ifPresent(value -> permitLimiter.release(properties.getSemaphoreName(), value));
        }
    }

    private boolean isUploadRequest(HttpServletRequest request) {
        return UPLOAD_METHOD.equalsIgnoreCase(request.getMethod())
                && UPLOAD_PATH.equals(request.getRequestURI());
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HTTP_TOO_MANY_REQUESTS);
        response.setContentType(APPLICATION_JSON_UTF8);
        response.getWriter().write("{\"code\":\"429\",\"message\":\"当前上传人数过多，请稍后重试\"}");
    }
}
