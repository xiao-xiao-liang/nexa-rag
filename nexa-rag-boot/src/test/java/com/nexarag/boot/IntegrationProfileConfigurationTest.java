package com.nexarag.boot;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成环境配置文件测试。
 */
class IntegrationProfileConfigurationTest {

    @Test
    void integrationProfileShouldEnableFlywayAndUseSecretPlaceholders() throws Exception {
        ClassPathResource resource = new ClassPathResource("application-integration.yml");

        assertThat(resource.exists()).isTrue();
        String content = resource.getContentAsString(StandardCharsets.UTF_8);
        assertThat(content).contains("enabled: true");
        assertThat(content).contains("${NEXA_MYSQL_PASSWORD:}");
        assertThat(content).contains("${NEXA_REDIS_PASSWORD:}");
        assertThat(content).contains("${NEXA_ELASTICSEARCH_PASSWORD:}");
        assertThat(content).doesNotContain("");
        assertThat(content).doesNotContain("722121" + "Zheng");
        assertThat(content).doesNotContain("");
    }
}
