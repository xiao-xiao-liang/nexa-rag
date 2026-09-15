package com.nexarag.infra.observability.langfuse.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Langfuse 配置属性测试。
 */
class LangfusePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertiesConfiguration.class));

    @Test
    void shouldAllowEmptyConnectionPropertiesWhenDisabled() {
        contextRunner.withPropertyValues("nexa.observability.langfuse.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LangfuseProperties properties = context.getBean(LangfuseProperties.class);
                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.isCaptureContent()).isFalse();
                });
    }

    @Test
    void shouldRejectMissingConnectionPropertiesWhenEnabled() {
        contextRunner.withPropertyValues("nexa.observability.langfuse.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("Langfuse");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LangfuseProperties.class)
    static class PropertiesConfiguration {
    }
}
