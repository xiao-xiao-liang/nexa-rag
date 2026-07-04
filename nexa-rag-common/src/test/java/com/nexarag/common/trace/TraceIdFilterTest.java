package com.nexarag.common.trace;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TraceId Servlet 过滤器测试。
 */
class TraceIdFilterTest {

    @Test
    void filterShouldPropagateTraceIdInServletRequest() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceIdContext.TRACE_ID_HEADER, "trace-001");
        FilterChain filterChain = (servletRequest, servletResponse) -> {
            // 1. 验证业务链路中可以读取 TraceId
            assertThat(TraceIdContext.getTraceId()).isEqualTo("trace-001");
            assertThat(MDC.get(TraceIdContext.TRACE_ID_MDC_KEY)).isEqualTo("trace-001");

            // 2. 验证统一响应会携带当前 TraceId
            Result<String> result = Results.success("ok");
            assertThat(result.traceId()).isEqualTo("trace-001");
        };

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader(TraceIdContext.TRACE_ID_HEADER)).isEqualTo("trace-001");
        assertThat(TraceIdContext.getTraceId()).isNull();
        assertThat(MDC.get(TraceIdContext.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void filterShouldGenerateTraceIdWhenRequestHeaderMissing() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (servletRequest, servletResponse) -> {
            // 1. 验证缺失请求头时会生成 TraceId 并写入 MDC
            assertThat(TraceIdContext.getTraceId()).isNotBlank();
            assertThat(MDC.get(TraceIdContext.TRACE_ID_MDC_KEY)).isEqualTo(TraceIdContext.getTraceId());
        };

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader(TraceIdContext.TRACE_ID_HEADER)).hasSize(32);
        assertThat(TraceIdContext.getTraceId()).isNull();
        assertThat(MDC.get(TraceIdContext.TRACE_ID_MDC_KEY)).isNull();
    }
}
