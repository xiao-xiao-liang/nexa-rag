package com.nexarag.model.client.vllm;

import com.nexarag.infra.observability.langfuse.config.LangfuseProperties;
import com.nexarag.model.config.ModelProfileProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * vLLM Tokenizer 客户端测试。
 */
class VllmTokenizerClientTest {

    private HttpServer server;
    private ExecutorService serverExecutor;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void countTokensShouldPostToRootTokenizerEndpointAndCacheResult() throws Exception {
        List<String> requestBodies = new ArrayList<>();
        startServer(exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, "{\"count\":17}");
        });
        VllmTokenizerClient client = client(800, 2);

        Integer first = client.countTokens(profile(), "一段用于统计的证据").block();
        Integer second = client.countTokens(profile(), "一段用于统计的证据").block();

        assertThat(first).isEqualTo(17);
        assertThat(second).isEqualTo(17);
        assertThat(requestBodies).hasSize(1);
        assertThat(requestBodies.getFirst()).contains("\"model\":\"Qwen3.5-4B\"")
                .contains("\"prompt\":\"一段用于统计的证据\"");
    }

    @Test
    void countTokensShouldReturnEmptyWhenTokenizerFailsOrTimesOut() throws Exception {
        startServer(exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (requestBody.contains("超时")) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                writeJson(exchange, 200, "{\"count\":17}");
                return;
            }
            writeJson(exchange, 503, "{\"error\":\"unavailable\"}");
        });
        VllmTokenizerClient client = client(50, 2);

        Integer unavailable = client.countTokens(profile(), "证据").block();
        Integer timeout = client.countTokens(profile(), "超时证据").block();

        assertThat(unavailable).isNull();
        assertThat(timeout).isNull();
    }

    @Test
    void countTokensShouldLimitConcurrentRequests() throws Exception {
        AtomicInteger activeRequests = new AtomicInteger();
        AtomicInteger maximumActiveRequests = new AtomicInteger();
        CountDownLatch bothRequestsCompleted = new CountDownLatch(2);
        startServer(exchange -> {
            exchange.getRequestBody().readAllBytes();
            int active = activeRequests.incrementAndGet();
            maximumActiveRequests.accumulateAndGet(active, Math::max);
            try {
                Thread.sleep(80);
                writeJson(exchange, 200, "{\"count\":3}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                activeRequests.decrementAndGet();
                bothRequestsCompleted.countDown();
            }
        });
        VllmTokenizerClient client = client(800, 1);

        List<Integer> values = Mono.zip(
                        client.countTokens(profile(), "片段一"),
                        client.countTokens(profile(), "片段二"))
                .map(tuple -> List.of(tuple.getT1(), tuple.getT2()))
                .block();

        assertThat(values).containsExactly(3, 3);
        assertThat(bothRequestsCompleted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(maximumActiveRequests).hasValue(1);
    }

    private VllmTokenizerClient client(long timeoutMs, int maxConcurrency) {
        LangfuseProperties properties = new LangfuseProperties();
        properties.setEnabled(true);
        properties.getTokenizer().setTimeoutMs(timeoutMs);
        properties.getTokenizer().setMaxConcurrency(maxConcurrency);
        properties.getTokenizer().setCacheMaximumSize(10);
        return new VllmTokenizerClient(WebClient.builder(), properties);
    }

    private ModelProfileProperties profile() {
        return ModelProfileProperties.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                .modelName("Qwen3.5-4B")
                .build();
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tokenize", exchange -> handler.handle(exchange));
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {

        void handle(HttpExchange exchange) throws IOException;
    }
}
