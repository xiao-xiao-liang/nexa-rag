package com.nexarag.infra.source.feishu;

import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.config.CloudDocumentProperties;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 飞书 DOCX 来源读取器测试。
 */
class FeishuDocxSourceReaderTest {

    @TempDir
    Path tempDir;

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handleRequest);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void readShouldPassDocumentTokenWhenPollingExportTask() throws Exception {
        ArtifactProcessingProperties artifactProperties = new ArtifactProcessingProperties();
        artifactProperties.setTempRoot(tempDir);
        artifactProperties.setMaxWorkspaceBytes(1024L);
        CloudDocumentProperties cloudProperties = buildCloudProperties();
        FeishuDocxSourceReader reader = new FeishuDocxSourceReader(cloudProperties, artifactProperties,
                new BoundedFileTransfer());
        ArtifactWorkspaceFactory workspaceFactory = new ArtifactWorkspaceFactory(artifactProperties);

        try (ArtifactWorkspace workspace = workspaceFactory.create(1001L)) {
            SourceReadResultBO result = reader.read(new SourceReadRequestDTO(1001L,
                    ExternalDocumentSourceType.FEISHU, "https://tenant.feishu.cn/wiki/wiki-token"), workspace);

            assertThat(result.externalDocumentId()).isEqualTo("docx-token");
            assertThat(Files.readString(result.sourcePath())).isEqualTo("DOCX");
        }
    }

    private CloudDocumentProperties buildCloudProperties() {
        CloudDocumentProperties properties = new CloudDocumentProperties();
        properties.getFeishu().setAppId("test-app-id");
        properties.getFeishu().setAppSecret("test-app-secret");
        properties.getFeishu().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return properties;
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
        if ("/open-apis/drive/v1/export_tasks".equals(path)) {
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

    private void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
