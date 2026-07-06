package com.nexarag.model.provider;

import com.nexarag.model.client.RerankClientFactory;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.rerank.RerankCandidate;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.model.route.ModelRouteDecision;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rerank Provider 测试。
 */
class RerankProviderTest {

    @Test
    void qwen3RerankShouldUseCompatibleApi() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/compatible-api/v1/reranks", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] responseBody = """
                    {"object":"list","results":[{"index":0,"relevance_score":0.91}],"model":"qwen3-rerank","usage":{"total_tokens":20}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        httpServer.start();
        try {
            RerankProvider provider = new RerankProvider(new RerankClientFactory(RestClient.builder()));
            ModelRouteDecision decision = routeDecision("http://127.0.0.1:" + httpServer.getAddress().getPort()
                    + "/api/v1", "qwen3-rerank", null);

            RerankModelResponse response = provider.rerank(decision, request());

            assertThat(requestBody.get())
                    .contains("\"model\":\"qwen3-rerank\"")
                    .contains("\"documents\":[\"片段内容\"]")
                    .contains("\"query\":\"问题\"")
                    .contains("\"top_n\":1")
                    .doesNotContain("\"input\"")
                    .doesNotContain("\"parameters\"");
            assertThat(response.modelProfile()).isEqualTo("rerank-primary");
            assertThat(response.totalTokens()).isEqualTo(20);
            assertThat(response.scores()).hasSize(1);
            assertThat(response.scores().getFirst().id()).isEqualTo("chunk-1");
            assertThat(response.scores().getFirst().score()).isEqualTo(0.91);
        } finally {
            httpServer.stop(0);
        }
    }

    @Test
    void serviceRerankShouldUseConfiguredEndpointPath() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/v1/services/rerank/text-rerank/text-rerank", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] responseBody = """
                    {"output":{"results":[{"index":0,"relevance_score":0.88}]},"usage":{"total_tokens":10}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        httpServer.start();
        try {
            RerankProvider provider = new RerankProvider(new RerankClientFactory(RestClient.builder()));
            ModelRouteDecision decision = routeDecision("http://127.0.0.1:" + httpServer.getAddress().getPort()
                    + "/api/v1", "gte-rerank-v2", "/services/rerank/text-rerank/text-rerank");

            RerankModelResponse response = provider.rerank(decision, request());

            assertThat(requestBody.get())
                    .contains("\"input\":{\"query\":\"问题\",\"documents\":[\"片段内容\"]}")
                    .contains("\"parameters\":{\"return_documents\":true,\"top_n\":1}");
            assertThat(response.totalTokens()).isEqualTo(10);
            assertThat(response.scores().getFirst().id()).isEqualTo("chunk-1");
            assertThat(response.scores().getFirst().score()).isEqualTo(0.88);
        } finally {
            httpServer.stop(0);
        }
    }

    @Test
    void shouldSupportRerankProviders() {
        RerankProvider provider = new RerankProvider(new RerankClientFactory(RestClient.builder()));

        assertThat(provider.supports(ModelProvider.DASHSCOPE, ModelType.RERANK)).isTrue();
        assertThat(provider.supports(ModelProvider.OPENAI, ModelType.RERANK)).isTrue();
        assertThat(provider.supports(ModelProvider.DASHSCOPE, ModelType.EMBEDDING)).isFalse();
    }

    private RerankModelRequest request() {
        return RerankModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.RERANK)
                .bizId("conversation-1")
                .routeKey("rerank")
                .query("问题")
                .candidates(List.of(new RerankCandidate("chunk-1", "片段内容", Map.of("documentId", 1L))))
                .build();
    }

    private ModelRouteDecision routeDecision(String baseUrl, String modelName, String endpointPath) {
        ModelProfileProperties profile = new ModelProfileProperties();
        profile.setProvider("DASHSCOPE");
        profile.setBaseUrl(baseUrl);
        profile.setEndpointPath(endpointPath);
        profile.setApiKey("sk-test");
        profile.setModelName(modelName);
        return new ModelRouteDecision("rerank-primary", profile, false);
    }
}
