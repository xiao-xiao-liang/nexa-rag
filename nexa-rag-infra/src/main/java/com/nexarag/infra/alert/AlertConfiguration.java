package com.nexarag.infra.alert;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 告警外部客户端装配配置。
 */
@Configuration
public class AlertConfiguration {

    /**
     * 创建供外部告警渠道使用的HTTP客户端。
     *
     * @param builder Spring Boot提供的HTTP客户端构建器
     * @return 告警HTTP客户端
     */
    @Bean
    public RestClient alertRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}
