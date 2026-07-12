package com.nexarag.infra.parser.mineru;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.MinerUProperties;
import com.nexarag.infra.enums.MinerUClientMode;
import com.nexarag.infra.parser.mineru.client.OfficialMinerUClient;
import com.nexarag.infra.parser.model.MinerUParseCommand;
import com.nexarag.infra.parser.model.MinerUParseResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 官方 MinerU 客户端测试，验证鉴权、上传、轮询和结果下载协议。
 */
class OfficialMinerUClientTest {

    private static final String API_KEY = "test-api-key";
    private static final byte[] FILE_BYTES = "pdf-content".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ZIP_BYTES = "zip-content".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parseShouldRejectBlankApiKey() {
        MinerUProperties properties = new MinerUProperties();
        properties.setMode(MinerUClientMode.OFFICIAL);

        assertThatThrownBy(() -> createClient(properties).parse(command()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("API Key");
    }

    @Test
    void parseShouldRejectApplyResponseWithoutBatchId() throws Exception {
        startServer();
        server.createContext("/api/v4/file-urls/batch", exchange -> respondJson(exchange, 200, """
                {"code":0,"data":{"file_urls":["http://127.0.0.1/upload"]},"msg":"ok"}
                """));

        assertThatThrownBy(() -> createClient(properties()).parse(command()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("申请上传地址")
                .hasMessageContaining("缺少字段=batchId")
                .hasMessageContaining("documentId=1")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void parseShouldUploadPollAndDownloadZip() throws Exception {
        AtomicReference<String> applyAuthorization = new AtomicReference<>();
        AtomicReference<String> applyBody = new AtomicReference<>();
        AtomicReference<String> uploadAuthorization = new AtomicReference<>();
        AtomicReference<String> uploadContentType = new AtomicReference<>();
        AtomicReference<String> uploadRawQuery = new AtomicReference<>();
        AtomicReference<String> downloadRawQuery = new AtomicReference<>();
        AtomicReference<byte[]> uploadBody = new AtomicReference<>();
        AtomicInteger pollCount = new AtomicInteger();
        startServer();
        String baseUrl = baseUrl();

        server.createContext("/api/v4/file-urls/batch", exchange -> {
            applyAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            applyBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, 200, """
                    {"code":0,"data":{"batch_id":"batch-1","file_urls":["%s/upload?Signature=a%%2Fb"]},"msg":"ok"}
                    """.formatted(baseUrl));
        });
        server.createContext("/upload", exchange -> {
            uploadAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            uploadContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            uploadRawQuery.set(exchange.getRequestURI().getRawQuery());
            uploadBody.set(exchange.getRequestBody().readAllBytes());
            respondBytes(exchange, 200, new byte[0], "application/octet-stream");
        });
        server.createContext("/api/v4/extract-results/batch/batch-1", exchange -> {
            int currentCount = pollCount.incrementAndGet();
            String body = currentCount == 1
                    ? "{\"code\":0,\"data\":{\"extract_result\":[{\"file_name\":\"demo.pdf\",\"state\":\"running\"}]},\"msg\":\"ok\"}"
                    : "{\"code\":0,\"data\":{\"extract_result\":[{\"file_name\":\"demo.pdf\",\"state\":\"done\",\"full_zip_url\":\"%s/result.zip?Signature=c%%2Fd\"}]},\"msg\":\"ok\"}"
                    .formatted(baseUrl);
            respondJson(exchange, 200, body);
        });
        server.createContext("/result.zip", exchange -> {
            downloadRawQuery.set(exchange.getRequestURI().getRawQuery());
            respondBytes(exchange, 200, ZIP_BYTES, "application/zip");
        });

        MinerUParseResponse response = createClient(properties()).parse(command());

        assertThat(response.zipInputStream().readAllBytes()).isEqualTo(ZIP_BYTES);
        assertThat(response.metadata())
                .containsEntry("clientMode", "official")
                .containsEntry("batchId", "batch-1")
                .containsEntry("pollCount", 2)
                .containsEntry("zipSize", ZIP_BYTES.length)
                .doesNotContainValue(API_KEY)
                .doesNotContainValue(baseUrl + "/upload")
                .doesNotContainValue(baseUrl + "/result.zip");
        assertThat(applyAuthorization.get()).isEqualTo("Bearer " + API_KEY);
        assertThat(applyBody.get())
                .contains("\"name\":\"demo.pdf\"")
                .contains("\"data_id\":\"document-1\"")
                .contains("\"model_version\":\"vlm\"");
        assertThat(uploadAuthorization.get()).isNull();
        assertThat(uploadContentType.get()).isNull();
        assertThat(uploadRawQuery.get()).isEqualTo("Signature=a%2Fb");
        assertThat(downloadRawQuery.get()).isEqualTo("Signature=c%2Fd");
        assertThat(uploadBody.get()).isEqualTo(FILE_BYTES);
    }

    @Test
    void parseShouldFailWhenOfficialTaskFailed() throws Exception {
        startServerWithApplyAndUpload();
        server.createContext("/api/v4/extract-results/batch/batch-1", exchange -> respondJson(exchange, 200, """
                {"code":0,"data":{"extract_result":[{"file_name":"demo.pdf","state":"failed","err_msg":"parse failed"}]},"msg":"ok"}
                """));

        assertThatThrownBy(() -> createClient(properties()).parse(command()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("官方解析任务失败")
                .hasMessageContaining("parse failed")
                .hasMessageContaining("documentId=1")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void parseShouldTimeoutAfterConfiguredPollCount() throws Exception {
        AtomicInteger pollCount = new AtomicInteger();
        startServerWithApplyAndUpload();
        server.createContext("/api/v4/extract-results/batch/batch-1", exchange -> {
            pollCount.incrementAndGet();
            respondJson(exchange, 200, """
                    {"code":0,"data":{"extract_result":[{"file_name":"demo.pdf","state":"running"}]},"msg":"ok"}
                    """);
        });
        MinerUProperties properties = properties();
        properties.setMaxPollCount(2);

        assertThatThrownBy(() -> createClient(properties).parse(command()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("轮询超时")
                .hasMessageContaining("documentId=1");
        assertThat(pollCount.get()).isEqualTo(2);
    }

    private void startServerWithApplyAndUpload() throws Exception {
        startServer();
        String baseUrl = baseUrl();
        server.createContext("/api/v4/file-urls/batch", exchange -> respondJson(exchange, 200, """
                {"code":0,"data":{"batch_id":"batch-1","file_urls":["%s/upload"]},"msg":"ok"}
                """.formatted(baseUrl)));
        server.createContext("/upload", exchange ->
                respondBytes(exchange, 200, new byte[0], "application/octet-stream"));
    }

    private void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    private MinerUProperties properties() {
        MinerUProperties properties = new MinerUProperties();
        properties.setMode(MinerUClientMode.OFFICIAL);
        properties.setOfficialEndpoint(baseUrl());
        properties.setApiKey(API_KEY);
        properties.setPollInterval(Duration.ZERO);
        return properties;
    }

    private OfficialMinerUClient createClient(MinerUProperties properties) {
        return new OfficialMinerUClient(properties, new ObjectMapper());
    }

    private MinerUParseCommand command() {
        return MinerUParseCommand.builder()
                .documentId(1L)
                .fileName("demo.pdf")
                .inputStream(new ByteArrayInputStream(FILE_BYTES))
                .enableOcr(true)
                .build();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respondJson(HttpExchange exchange, int statusCode, String responseBody) {
        respondBytes(exchange, statusCode, responseBody.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    private void respondBytes(HttpExchange exchange, int statusCode, byte[] responseBody, String contentType) {
        try {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(statusCode, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
