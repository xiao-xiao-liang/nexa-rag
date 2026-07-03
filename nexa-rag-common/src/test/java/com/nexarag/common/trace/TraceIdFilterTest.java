package com.nexarag.common.trace;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
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
        FilterChain filterChain = (servletRequest, servletResponse) ->
                assertThat(TraceIdContext.getTraceId()).isEqualTo("trace-001");

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader(TraceIdContext.TRACE_ID_HEADER)).isEqualTo("trace-001");
        assertThat(TraceIdContext.getTraceId()).isNull();
    }
}
