package com.nexarag.boot.config;

import com.nexarag.auth.filter.FixedUserAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册认证模块的 Web 请求过滤器。
 */
@Configuration
public class AuthWebConfiguration {

    /**
     * 注册固定本地用户过滤器，为后续接入 Sa-Token 保留替换点。
     *
     * @return 过滤器注册信息
     */
    @Bean
    public FilterRegistrationBean<FixedUserAuthenticationFilter> fixedUserAuthenticationFilter() {
        FilterRegistrationBean<FixedUserAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new FixedUserAuthenticationFilter());
        registration.addUrlPatterns("/*");
        registration.setName("fixedUserAuthenticationFilter");
        registration.setOrder(-100);
        return registration;
    }
}
