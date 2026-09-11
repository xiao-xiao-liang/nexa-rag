package com.nexarag.infra.source.feishu;

import com.nexarag.infra.config.CloudDocumentProperties;
import com.nexarag.infra.parser.workspace.BoundedFileTransfer;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 飞书 Block 媒体下载器测试。 */
class FeishuBlockMediaDownloaderTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private CloudDocumentProperties properties;
    private FeishuBlockMediaDownloader downloader;
    private final List<String> requestedPaths = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handleRequest);
        server.start();
        properties = new CloudDocumentProperties();
        properties.getFeishu().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        downloader = new FeishuBlockMediaDownloader(properties, new BoundedFileTransfer());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void downloadShouldFallbackToPreviewEndpointAndStreamToWorkspace() throws Exception {
        FeishuBlockMediaDownloadResultBO result = downloader.download("tenant-token", "docx-token", "image-1",
                27, "image-token", tempDir, 3L * 1024 * 1024);

        assertThat(result.status()).isEqualTo(FeishuBlockMediaDownloadResultBO.Status.DOWNLOADED);
        assertThat(result.relativePath()).isEqualTo("assets/image-1.png");
        assertThat(Files.size(result.file())).isEqualTo(2L * 1024 * 1024);
        assertThat(requestedPaths).containsExactly(
                "/open-apis/drive/v1/medias/image-token/download",
                "/open-apis/drive/v1/medias/image-token/preview_download");
    }

    @Test
    void downloadShouldSkipWhenSingleAssetLimitIsExceeded() {
        properties.getFeishu().getBlockMedia().setMaxSingleAssetBytes(1L);

        FeishuBlockMediaDownloadResultBO result = downloader.download("tenant-token", "docx-token", "image-1",
                27, "image-token", tempDir, 1024L);

        assertThat(result.status()).isEqualTo(FeishuBlockMediaDownloadResultBO.Status.SKIPPED);
        assertThat(result.failureReason()).isEqualTo("SINGLE_ASSET_SIZE_LIMIT_EXCEEDED");
        assertThat(result.file()).isNull();
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requestedPaths.add(path);
        if (path.endsWith("/download")) {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
            return;
        }
        byte[] body = new byte[2 * 1024 * 1024];
        for (int index = 0; index < body.length; index += 8192) {
            byte[] chunk = "media".getBytes(StandardCharsets.UTF_8);
            System.arraycopy(chunk, 0, body, index, Math.min(chunk.length, body.length - index));
        }
        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
