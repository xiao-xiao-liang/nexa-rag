package com.nexarag.boot.ratelimit;

import com.nexarag.infra.ratelimit.DistributedPermitLimiter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档上传限流过滤器测试。
 */
class DocumentUploadRateLimitFilterTest {

    @Test
    void doFilterShouldRejectUploadWhenPermitUnavailable() throws Exception {
        DocumentUploadRateLimitProperties properties = buildProperties();
        DistributedPermitLimiter permitLimiter = mock(DistributedPermitLimiter.class);
        when(permitLimiter.acquire("nexa:document:upload", 1, 300)).thenReturn(Optional.empty());
        DocumentUploadRateLimitFilter filter = new DocumentUploadRateLimitFilter(properties, permitLimiter);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/documents/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("当前上传人数过多");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void doFilterShouldReleasePermitAfterUploadRequest() throws Exception {
        DocumentUploadRateLimitProperties properties = buildProperties();
        DistributedPermitLimiter permitLimiter = mock(DistributedPermitLimiter.class);
        when(permitLimiter.acquire("nexa:document:upload", 1, 300)).thenReturn(Optional.of("permit-1"));
        DocumentUploadRateLimitFilter filter = new DocumentUploadRateLimitFilter(properties, permitLimiter);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/documents/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(permitLimiter).release("nexa:document:upload", "permit-1");
    }

    @Test
    void doFilterShouldSkipNonUploadRequest() throws Exception {
        DocumentUploadRateLimitProperties properties = buildProperties();
        DistributedPermitLimiter permitLimiter = mock(DistributedPermitLimiter.class);
        DocumentUploadRateLimitFilter filter = new DocumentUploadRateLimitFilter(properties, permitLimiter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(permitLimiter, never()).acquire("nexa:document:upload", 1, 300);
    }

    private DocumentUploadRateLimitProperties buildProperties() {
        DocumentUploadRateLimitProperties properties = new DocumentUploadRateLimitProperties();
        properties.setEnabled(true);
        properties.setSemaphoreName("nexa:document:upload");
        properties.setMaxWaitSeconds(1);
        properties.setLeaseSeconds(300);
        return properties;
    }
}
