package com.nexarag.infra.observability.langfuse;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.observability.langfuse.config.LangfuseProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Langfuse 查询客户端测试。
 */
class LangfuseQueryClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldRequestBoundedGenerationFieldsWithBasicAuth() throws Exception {
        AtomicReference<String> requestUri = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        startServer(exchange -> {
            requestUri.set(exchange.getRequestURI().toString());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"data\":[{\"id\":\"obs-1\",\"traceId\":\"trace-1\",\"type\":\"GENERATION\"}],\"meta\":{\"cursor\":\"next\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        LangfuseQueryClient client = new LangfuseQueryClient(RestClient.builder(), properties());

        var result = client.fetchGenerations(Instant.parse("2026-09-10T00:00:00Z"),
                Instant.parse("2026-09-11T00:00:00Z"), "previous");

        assertThat(result.data()).hasSize(1);
        assertThat(result.meta().cursor()).isEqualTo("next");
        assertThat(requestUri.get()).startsWith("/api/public/v2/observations?")
                .contains("type=GENERATION")
                .contains("fields=core,basic,usage,metrics,metadata,model")
                .contains("cursor=previous");
        assertThat(authorization.get()).isEqualTo("Basic cGs6c2s=");
    }

    @Test
    void shouldNormalizeRemoteFailureAsUnavailable() throws Exception {
        startServer(exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        LangfuseQueryClient client = new LangfuseQueryClient(RestClient.builder(), properties());

        assertThatThrownBy(() -> client.fetchGenerations(Instant.parse("2026-09-10T00:00:00Z"),
                Instant.parse("2026-09-11T00:00:00Z"), null))
                .isInstanceOf(ServiceException.class)
                .extracting(exception -> ((ServiceException) exception).getErrorCode())
                .isEqualTo(BaseErrorCode.REMOTE_ERROR.code());
    }

    private LangfuseProperties properties() {
        LangfuseProperties properties = new LangfuseProperties();
        properties.setEnabled(true);
        properties.setHost(java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        properties.setPublicKey("pk");
        properties.setSecretKey("sk");
        return properties;
    }

    private void startServer(ExchangeHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/public/v2/observations", exchange -> handler.handle(exchange));
        server.start();
    }

    @FunctionalInterface
    private interface ExchangeHandler {

        void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException;
    }
}
