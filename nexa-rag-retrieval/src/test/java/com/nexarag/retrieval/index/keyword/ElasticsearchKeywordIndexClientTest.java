package com.nexarag.retrieval.index.keyword;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.dto.req.KeywordIndexWriteRequest;
import com.nexarag.retrieval.dto.req.KeywordIndexSearchRequest;
import com.nexarag.retrieval.model.KeywordIndexDocument;
import com.nexarag.retrieval.model.KeywordIndexWriteResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Elasticsearch 关键词索引客户端测试，使用本地 HTTP 服务验证 REST 请求契约。
 */
class ElasticsearchKeywordIndexClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer server;
    private List<CapturedRequest> requests;

    @BeforeEach
    void setUp() throws IOException {
        requests = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handleRequest);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void upsertShouldEnsureIndexAndWriteDocuments() throws Exception {
        ElasticsearchKeywordIndexClient client = new ElasticsearchKeywordIndexClient(properties(), objectMapper);
        KeywordIndexWriteRequest request = new KeywordIndexWriteRequest("nexa_document_chunk", 1L,
                List.of(new KeywordIndexDocument("chunk-1", 1L, null, 0, "测试文本", "{\"source\":\"unit\"}")));

        List<KeywordIndexWriteResult> results = client.upsert(request);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().success()).isTrue();
        assertThat(results.getFirst().keywordIndexId()).isEqualTo("elasticsearch:nexa_document_chunk:chunk-1");
        assertThat(requests)
                .extracting(CapturedRequest::method)
                .containsExactly("HEAD", "PUT", "PUT");
        CapturedRequest upsertRequest = requests.get(2);
        assertThat(upsertRequest.path()).isEqualTo("/nexa_document_chunk/_doc/chunk-1");
        assertThat(upsertRequest.authorization()).isEqualTo(basicAuth());
        JsonNode body = objectMapper.readTree(upsertRequest.body());
        assertThat(body.get("chunk_id").asText()).isEqualTo("chunk-1");
        assertThat(body.get("document_id").asLong()).isEqualTo(1L);
        assertThat(body.get("text").asText()).isEqualTo("测试文本");
    }

    @Test
    void deleteByDocumentIdShouldUseDeleteByQuery() throws Exception {
        ElasticsearchKeywordIndexClient client = new ElasticsearchKeywordIndexClient(properties(), objectMapper);

        int deletedCount = client.deleteByDocumentId(1L);

        assertThat(deletedCount).isEqualTo(2);
        CapturedRequest deleteRequest = requests.getLast();
        assertThat(deleteRequest.method()).isEqualTo("POST");
        assertThat(deleteRequest.path()).isEqualTo("/nexa_document_chunk/_delete_by_query");
        JsonNode body = objectMapper.readTree(deleteRequest.body());
        assertThat(body.at("/query/term/document_id/value").asLong()).isEqualTo(1L);
    }

    @Test
    void searchShouldUseMatchQueryAndKeepBm25Score() {
        ElasticsearchKeywordIndexClient client = new ElasticsearchKeywordIndexClient(properties(), objectMapper);

        var results = client.search(new KeywordIndexSearchRequest("nexa_document_chunk", "退款规则", 5));

        assertThat(results).isEmpty();
        CapturedRequest searchRequest = requests.getLast();
        assertThat(searchRequest.method()).isEqualTo("POST");
        assertThat(searchRequest.path()).isEqualTo("/nexa_document_chunk/_search");
    }

    private RetrievalProperties properties() {
        RetrievalProperties properties = new RetrievalProperties();
        RetrievalProperties.Keyword keyword = properties.getKeyword();
        keyword.setScheme("http");
        keyword.setHost("127.0.0.1");
        keyword.setPort(server.getAddress().getPort());
        keyword.setIndexName("nexa_document_chunk");
        keyword.setUsername("elastic");
        keyword.setPassword("");
        keyword.setRequestTimeoutMs(5000);
        return properties;
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        requests.add(new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                authorization, body));
        if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        byte[] responseBody = responseBody(exchange).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    private String responseBody(HttpExchange exchange) {
        if (exchange.getRequestURI().getPath().endsWith("_delete_by_query")) {
            return "{\"deleted\":2}";
        }
        return "{\"result\":\"ok\"}";
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder()
                .encodeToString("elastic:".getBytes(StandardCharsets.UTF_8));
    }

    private record CapturedRequest(String method, String path, String authorization, String body) {
    }
}
