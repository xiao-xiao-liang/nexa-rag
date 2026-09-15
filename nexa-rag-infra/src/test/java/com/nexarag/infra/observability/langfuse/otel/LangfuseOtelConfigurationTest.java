package com.nexarag.infra.observability.langfuse.otel;

import com.nexarag.infra.observability.langfuse.config.LangfuseProperties;
import com.nexarag.infra.observability.langfuse.aop.LangfuseTelemetryAspect;
import com.nexarag.infra.observability.langfuse.aop.LangfuseExpressionResolver;
import com.nexarag.infra.observability.langfuse.LangfuseTelemetry;
import com.nexarag.infra.observability.langfuse.model.LangfuseTraceCommand;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Langfuse OTLP 配置测试。
 */
class LangfuseOtelConfigurationTest {

    @Test
    void shouldProvideExpressionResolverForTelemetryAspect() {
        new ApplicationContextRunner()
                .withUserConfiguration(LangfuseOtelConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(LangfuseExpressionResolver.class));
    }

    @Test
    void shouldInstantiateTelemetryAspectWhenLangfuseIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(LangfuseOtelConfiguration.class)
                .withBean(LangfuseTelemetryAspect.class)
                .run(context -> assertThat(context).hasSingleBean(LangfuseTelemetryAspect.class));
    }

    @Test
    void shouldExportTraceToLangfuseOtlpEndpointWithRequiredHeaders() throws Exception {
        BlockingQueue<HttpExchangeSnapshot> requests = new ArrayBlockingQueue<>(1);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/public/otel/v1/traces", exchange -> respond(exchange, requests));
        server.start();
        try {
            LangfuseProperties properties = new LangfuseProperties();
            properties.setEnabled(true);
            properties.setHost(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            properties.setPublicKey("pk-test");
            properties.setSecretKey("sk-test");
            properties.setEnvironment("test");
            LangfuseOtelConfiguration configuration = new LangfuseOtelConfiguration();

            try (var tracerProvider = configuration.langfuseTracerProvider(properties)) {
                LangfuseTelemetry telemetry = configuration.langfuseTelemetry(tracerProvider, properties);
                telemetry.startTrace(new LangfuseTraceCommand("rag.chat", "trace-001", "conversation-001",
                        "user-001", Map.of("nexa.generation_id", "generation-001"))).close();
                tracerProvider.forceFlush().join(3, TimeUnit.SECONDS);
            }

            HttpExchangeSnapshot request = requests.poll(3, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.path()).isEqualTo("/api/public/otel/v1/traces");
            assertThat(request.authorization()).startsWith("Basic ");
            assertThat(request.ingestionVersion()).isEqualTo("4");
            assertThat(request.bodySize()).isPositive();
        } finally {
            server.stop(0);
        }
    }

    private void respond(HttpExchange exchange, BlockingQueue<HttpExchangeSnapshot> requests) throws java.io.IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        requests.offer(new HttpExchangeSnapshot(exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("x-langfuse-ingestion-version"), body.length));
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }

    private record HttpExchangeSnapshot(String path, String authorization, String ingestionVersion, int bodySize) {
    }
}
