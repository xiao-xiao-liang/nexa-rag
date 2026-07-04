package com.nexarag.boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动类配置测试。
 */
class NexaRagApplicationConfigurationTest {

    @Test
    void applicationShouldNotExcludeDataSourceAutoConfiguration() {
        SpringBootApplication annotation = NexaRagApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(annotation).isNotNull();
        assertThat(Arrays.asList(annotation.exclude())).doesNotContain(DataSourceAutoConfiguration.class);
    }
    @Test
    void defaultApplicationShouldConfigureRedisConnectionPlaceholders() throws Exception {
        org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("application.yml");

        assertThat(resource.exists()).isTrue();
        String content = resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("redis:");
        assertThat(content).contains("host: ${NEXA_REDIS_HOST:192.168.0.134}");
        assertThat(content).contains("port: ${NEXA_REDIS_PORT:6379}");
        assertThat(content).contains("password: ${NEXA_REDIS_PASSWORD:}");
        assertThat(content).contains("timeout: ${NEXA_REDIS_TIMEOUT:3s}");
    }
}
