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
}
