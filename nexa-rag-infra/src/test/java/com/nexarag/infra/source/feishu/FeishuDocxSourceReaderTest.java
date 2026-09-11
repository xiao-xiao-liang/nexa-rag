package com.nexarag.infra.source.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.config.CloudDocumentProperties;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import com.nexarag.infra.parser.workspace.ArtifactWorkspaceFactory;
import com.nexarag.infra.parser.workspace.BoundedFileTransfer;
import com.nexarag.infra.source.model.SourceReadRequestDTO;
import com.nexarag.infra.source.model.SourceReadResultBO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 飞书 Docx 来源读取器测试。
 */
class FeishuDocxSourceReaderTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private ArtifactProcessingProperties artifactProperties;
    private CloudDocumentProperties cloudProperties;
    private FeishuDocxSourceReader reader;
    private ArtifactWorkspaceFactory workspaceFactory;
    private final Map<String, String> blockPageResponses = new HashMap<>();
    private final List<String> requestedBlockPageTokens = new ArrayList<>();
    private boolean exportTaskRequested;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handleRequest);
        server.start();

        artifactProperties = new ArtifactProcessingProperties();
        artifactProperties.setTempRoot(tempDir);
        artifactProperties.setMaxWorkspaceBytes(1024L);
        cloudProperties = buildCloudProperties();
        reader = new FeishuDocxSourceReader(cloudProperties, artifactProperties, new BoundedFileTransfer(),
                new FeishuBlockMarkdownConverter(), new FeishuBlockMediaDownloader(cloudProperties,
                new BoundedFileTransfer()), new ObjectMapper());
        workspaceFactory = new ArtifactWorkspaceFactory(artifactProperties);
        stubBlocks("{\"code\":0,\"data\":{\"items\":[{\"block_id\":\"image\",\"block_type\":27}],\"has_more\":false}}");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void readShouldPassDocumentTokenWhenPollingExportTask() throws Exception {
        cloudProperties.getFeishu().getBlockRead().setEnabled(false);
        ReadOutcome outcome = readFeishuDocument();
        SourceReadResultBO result = outcome.result();

        assertThat(result.externalDocumentId()).isEqualTo("docx-token");
        assertThat(outcome.sourceContent()).isEqualTo("DOCX");
        assertThat(exportTaskRequested).isTrue();
    }

    @Test
    void readShouldPreferBlockMarkdownWhenBlocksContainHeadingsOnly() throws Exception {
        stubBlocks("{\"code\":0,\"data\":{\"items\":["
                + "{\"block_id\":\"root\",\"block_type\":1,\"children\":[\"h1\"]},"
                + "{\"block_id\":\"h1\",\"block_type\":3,\"heading1\":{\"elements\":[{\"text_run\":{\"content\":\"一级标题\"}}]}}],"
                + "\"has_more\":false}}");

        ReadOutcome outcome = readFeishuDocument();
        SourceReadResultBO result = outcome.result();

        assertThat(result.documentFormat()).isEqualTo(DocumentFormat.MARKDOWN);
        assertThat(result.sourceContentType()).isEqualTo("text/markdown");
        assertThat(outcome.sourceContent()).isEqualTo("# 一级标题");
        assertThat(result.metadata()).containsEntry("reader", "FEISHU_BLOCK_API");
        assertThat(exportTaskRequested).isFalse();
    }

    @Test
    void readShouldFollowBlockPageTokenBeforeConvertingMarkdown() throws Exception {
        stubBlockPage(null, "{\"code\":0,\"data\":{\"items\":["
                + "{\"block_id\":\"root\",\"block_type\":1,\"children\":[\"h1\"]}],"
                + "\"has_more\":true,\"page_token\":\"next-page\"}}");
        stubBlockPage("next-page", "{\"code\":0,\"data\":{\"items\":["
                + "{\"block_id\":\"h1\",\"block_type\":3,\"heading1\":{\"elements\":[{\"text_run\":{\"content\":\"跨页标题\"}}]}}],"
                + "\"has_more\":false}}");

        ReadOutcome outcome = readFeishuDocument();
        SourceReadResultBO result = outcome.result();

        assertThat(result.documentFormat()).isEqualTo(DocumentFormat.MARKDOWN);
        assertThat(outcome.sourceContent()).isEqualTo("# 跨页标题");
        assertThat(requestedBlockPageTokens).containsExactly(null, "next-page");
    }

    @Test
    void readShouldKeepBlockMarkdownWhenBlocksContainImageWithoutToken() throws Exception {
        SourceReadResultBO result = readFeishuDocument().result();

        assertThat(result.documentFormat()).isEqualTo(DocumentFormat.MARKDOWN);
        assertThat(result.metadata()).containsEntry("reader", "FEISHU_BLOCK_API");
        assertThat(result.metadata().get("media"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("skippedCount", 1);
        assertThat(exportTaskRequested).isFalse();
    }

    @Test
    void readShouldFallbackToDocxWhenBlockCountExceedsLimit() throws Exception {
        cloudProperties.getFeishu().getBlockRead().setMaxBlockCount(1);
        stubBlocks("{\"code\":0,\"data\":{\"items\":["
                + "{\"block_id\":\"a\",\"block_type\":1},{\"block_id\":\"b\",\"block_type\":3}],"
                + "\"has_more\":false}}");

        SourceReadResultBO result = readFeishuDocument().result();

        assertThat(result.documentFormat()).isEqualTo(DocumentFormat.WORD);
        assertThat(result.metadata()).containsEntry("blockFallbackReason", "BLOCK_COUNT_LIMIT_EXCEEDED");
    }

    @Test
    void readShouldFallbackToDocxWhenBlockJsonBytesExceedLimit() throws Exception {
        cloudProperties.getFeishu().getBlockRead().setMaxJsonBytes(1L);
        stubBlocks("{\"code\":0,\"data\":{\"items\":[{\"block_id\":\"root\",\"block_type\":1}],"
                + "\"has_more\":false}}");

        SourceReadResultBO result = readFeishuDocument().result();

        assertThat(result.documentFormat()).isEqualTo(DocumentFormat.WORD);
        assertThat(result.metadata()).containsEntry("blockFallbackReason", "BLOCK_JSON_LIMIT_EXCEEDED");
    }

    @Test
    void readShouldFallbackToDocxWhenBlockApiReturnsBusinessFailure() throws Exception {
        stubBlocks("{\"code\":99992402,\"msg\":\"request failed\"}");

        SourceReadResultBO result = readFeishuDocument().result();

        assertThat(result.documentFormat()).isEqualTo(DocumentFormat.WORD);
        assertThat(result.metadata()).containsEntry("blockFallbackReason", "BLOCK_API_UNAVAILABLE");
        assertThat(exportTaskRequested).isTrue();
    }

    @Test
    void readShouldFallbackToDocxWhenBlockPaginationRepeatsToken() throws Exception {
        stubBlockPage(null, "{\"code\":0,\"data\":{\"items\":[],\"has_more\":true,\"page_token\":\"same-page\"}}");
        stubBlockPage("same-page", "{\"code\":0,\"data\":{\"items\":[],\"has_more\":true,\"page_token\":\"same-page\"}}");

        SourceReadResultBO result = readFeishuDocument().result();

        assertThat(result.documentFormat()).isEqualTo(DocumentFormat.WORD);
        assertThat(result.metadata()).containsEntry("blockFallbackReason", "INVALID_BLOCK_PAGINATION");
        assertThat(requestedBlockPageTokens).containsExactly(null, "same-page");
    }

    private ReadOutcome readFeishuDocument() throws Exception {
        try (ArtifactWorkspace workspace = workspaceFactory.create(1001L)) {
            SourceReadResultBO result = reader.read(new SourceReadRequestDTO(1001L,
                    ExternalDocumentSourceType.FEISHU, "https://tenant.feishu.cn/wiki/wiki-token"), workspace);
            return new ReadOutcome(result, Files.readString(result.sourcePath()));
        }
    }

    private CloudDocumentProperties buildCloudProperties() {
        CloudDocumentProperties properties = new CloudDocumentProperties();
        properties.getFeishu().setAppId("test-app-id");
        properties.getFeishu().setAppSecret("test-app-secret");
        properties.getFeishu().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return properties;
    }

    private void stubBlocks(String response) {
        stubBlockPage(null, response);
    }

    private void stubBlockPage(String pageToken, String response) {
        blockPageResponses.put(pageToken, response);
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/open-apis/auth/v3/tenant_access_token/internal".equals(path)) {
            respondJson(exchange, 200, "{\"tenant_access_token\":\"tenant-token\"}");
            return;
        }
        if ("/open-apis/wiki/v2/spaces/get_node".equals(path)) {
            if (!"token=wiki-token".equals(exchange.getRequestURI().getQuery())) {
                respondJson(exchange, 400, "{\"code\":99992402,\"msg\":\"token is required\"}");
                return;
            }
            respondJson(exchange, 200, "{\"code\":0,\"data\":{\"node\":{\"obj_type\":\"docx\",\"obj_token\":\"docx-token\"}}}");
            return;
        }
        if ("/open-apis/docx/v1/documents/docx-token".equals(path)) {
            respondJson(exchange, 200, "{\"code\":0,\"data\":{\"document\":{\"title\":\"测试文档\",\"revision_id\":\"revision-1\"}}}");
            return;
        }
        if ("/open-apis/docx/v1/documents/docx-token/blocks".equals(path)) {
            String pageToken = queryParameters(exchange).get("page_token");
            requestedBlockPageTokens.add(pageToken);
            String response = blockPageResponses.get(pageToken);
            respondJson(exchange, response == null ? 404 : 200, response == null ? "{\"code\":404}" : response);
            return;
        }
        if ("/open-apis/drive/v1/export_tasks".equals(path)) {
            exportTaskRequested = true;
            respondJson(exchange, 200, "{\"code\":0,\"data\":{\"ticket\":\"export-ticket\"}}");
            return;
        }
        if ("/open-apis/drive/v1/export_tasks/export-ticket".equals(path)) {
            if (!"token=docx-token".equals(exchange.getRequestURI().getQuery())) {
                respondJson(exchange, 400, "{\"code\":99992402,\"msg\":\"token is required\"}");
                return;
            }
            respondJson(exchange, 200, "{\"code\":0,\"data\":{\"result\":{\"file_token\":\"file-token\"}}}");
            return;
        }
        if ("/open-apis/drive/v1/export_tasks/file/file-token/download".equals(path)) {
            byte[] body = "DOCX".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }
        respondJson(exchange, 404, "{\"code\":404}");
    }

    private Map<String, String> queryParameters(HttpExchange exchange) {
        Map<String, String> parameters = new HashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return parameters;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            parameters.put(name, value);
        }
        return parameters;
    }

    private void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private record ReadOutcome(SourceReadResultBO result, String sourceContent) {
    }
}
