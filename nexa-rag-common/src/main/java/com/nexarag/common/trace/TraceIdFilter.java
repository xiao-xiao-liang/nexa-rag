package com.nexarag.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * TraceId Servlet 过滤器，用于在请求入口生成或透传链路 ID。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);

        try {
            // 1. 写入线程上下文和日志 MDC
            TraceIdContext.setTraceId(traceId);
            MDC.put(TraceIdContext.TRACE_ID_MDC_KEY, traceId);
            response.setHeader(TraceIdContext.TRACE_ID_HEADER, traceId);

            // 2. 放行请求
            filterChain.doFilter(request, response);
        } finally {
            // 3. 清理线程上下文，避免线程复用导致串号
            TraceIdContext.clear();
            MDC.remove(TraceIdContext.TRACE_ID_MDC_KEY);
        }
    }

    /**
     * 解析请求中的 TraceId。
     *
     * @param request Servlet 请求对象
     * @return TraceId
     */
    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TraceIdContext.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return traceId;
    }
}
