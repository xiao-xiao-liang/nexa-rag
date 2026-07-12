package com.nexarag.auth.filter;

import com.nexarag.auth.context.CurrentUserContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * 验证固定用户过滤器能够注入并清理请求用户身份。
 */
class FixedUserAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void shouldSetFixedUserDuringFilterChainAndClearAfterwards() throws Exception {
        FixedUserAuthenticationFilter filter = new FixedUserAuthenticationFilter();
        FilterChain filterChain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(CurrentUserContext.getRequired().userId()).isNotBlank();
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

        assertThatThrownBy(CurrentUserContext::getRequired)
                .isInstanceOf(IllegalStateException.class);
    }
}
