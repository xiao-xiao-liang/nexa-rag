package com.nexarag.auth.web;

import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.common.exception.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * CSRF 请求校验器测试。
 */
class CsrfRequestValidatorTest {

    private CsrfTokenService csrfTokenService;
    private CsrfRequestValidator validator;

    @BeforeEach
    void setUp() {
        csrfTokenService = mock(CsrfTokenService.class);
        validator = new CsrfRequestValidator(csrfTokenService);
        ReflectionTestUtils.setField(validator, "allowedOrigin", "http://localhost:3000");
    }

    /**
     * 来源不匹配时应返回专用错误码。
     */
    @Test
    void shouldRejectUnexpectedOriginWithDedicatedErrorCode() {
        MockHttpServletRequest request = request();
        request.addHeader(HttpHeaders.ORIGIN, "http://malicious.example");

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ClientException.class)
                .extracting(throwable -> ((ClientException) throwable).getErrorCode())
                .isEqualTo(AuthErrorCode.REQUEST_ORIGIN_VALIDATION_FAILED.code());
    }

    /**
     * 浏览器明确标记为跨站时应返回专用错误码。
     */
    @Test
    void shouldRejectCrossSiteFetchMetadataWithDedicatedErrorCode() {
        MockHttpServletRequest request = request();
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:3000");
        request.addHeader("Sec-Fetch-Site", "cross-site");

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ClientException.class)
                .extracting(throwable -> ((ClientException) throwable).getErrorCode())
                .isEqualTo(AuthErrorCode.FETCH_METADATA_VALIDATION_FAILED.code());
    }

    /**
     * 请求来源与上下文都合法时，应继续执行 CSRF token 校验。
     */
    @Test
    void shouldDelegateTokenValidationAfterRequestContextChecks() {
        MockHttpServletRequest request = request();
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:3000");
        request.addHeader("Sec-Fetch-Site", "same-origin");

        validator.validate(request);

        verify(csrfTokenService).validateCurrentRequestToken(request);
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", "/api/auth/email/send-code");
    }
}
