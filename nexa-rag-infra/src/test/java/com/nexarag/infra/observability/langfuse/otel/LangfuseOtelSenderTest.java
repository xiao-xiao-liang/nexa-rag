package com.nexarag.infra.observability.langfuse.otel;

import io.opentelemetry.sdk.common.export.HttpSenderProvider;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Langfuse OTLP HTTP 发送器装配测试。
 */
class LangfuseOtelSenderTest {

    @Test
    void shouldUseOkHttpSenderInsteadOfJdkSenderForPlainHttpEndpoint() {
        var providerNames = ServiceLoader.load(HttpSenderProvider.class).stream()
                .map(ServiceLoader.Provider::type)
                .map(Class::getName)
                .toList();

        assertThat(providerNames)
                .contains("io.opentelemetry.exporter.sender.okhttp.internal.OkHttpHttpSenderProvider")
                .doesNotContain("io.opentelemetry.exporter.sender.jdk.internal.JdkHttpSenderProvider");
    }
}
