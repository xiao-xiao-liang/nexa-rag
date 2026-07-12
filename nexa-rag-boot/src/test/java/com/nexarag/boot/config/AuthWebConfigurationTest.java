package com.nexarag.boot.config;

import com.nexarag.auth.filter.FixedUserAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 固定用户过滤器注册配置测试。
 */
class AuthWebConfigurationTest {

    @Test
    void shouldRegisterFixedUserFilterForAllRequests() {
        FilterRegistrationBean<FixedUserAuthenticationFilter> registration =
                new AuthWebConfiguration().fixedUserAuthenticationFilter();

        assertThat(registration.getFilter()).isInstanceOf(FixedUserAuthenticationFilter.class);
        assertThat(registration.getUrlPatterns()).containsExactly("/*");
        assertThat(registration.getOrder()).isEqualTo(-100);
    }
}
