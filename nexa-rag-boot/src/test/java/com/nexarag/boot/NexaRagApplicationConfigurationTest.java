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
    void defaultApplicationShouldConfigureRedisConnectionDefaults() throws Exception {
        org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("application.yml");

        assertThat(resource.exists()).isTrue();
        String content = resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("redis:");
        assertThat(content).contains("host: 192.168.0.134");
        assertThat(content).contains("port: 6379");
        assertThat(content).contains("password: ");
        assertThat(content).contains("timeout: 3s");
        assertThat(content).doesNotContain("${");
    }

    @Test
    void defaultApplicationShouldLimitMultipartUploadSize() throws Exception {
        org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("application.yml");

        String content = resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("max-file-size: 100MB");
        assertThat(content).contains("max-request-size: 110MB");
    }
}
