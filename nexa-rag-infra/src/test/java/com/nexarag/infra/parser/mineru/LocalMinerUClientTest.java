package com.nexarag.infra.parser.mineru;

import com.nexarag.infra.config.MinerUProperties;
import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.parser.mineru.client.LocalMinerUClient;
import com.nexarag.infra.parser.model.MinerUParseCommand;
import com.nexarag.infra.parser.model.MinerUParseResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地 MinerU 客户端测试。
 */
class LocalMinerUClientTest {

    private HttpServer server;
    private String requestBody;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parseShouldCallLocalFileParseApiAndReturnZipStream() throws Exception {
        byte[] zipBytes = zip(Map.of("content.md", "# title"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file_parse", exchange -> respondZip(exchange, zipBytes));
        server.start();
        MinerUProperties properties = new MinerUProperties();
        properties.setLocalEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        LocalMinerUClient client = new LocalMinerUClient(properties, artifactProperties());

        MinerUParseResponse response = client.parse(MinerUParseCommand.builder()
                .documentId(1L)
                .fileName("demo.pdf")
                .inputStream(new ReadAllBytesFailingInputStream("pdf".getBytes(StandardCharsets.UTF_8)))
                .enableOcr(true)
                .build());

        try (var zipInputStream = response.zipInputStream()) {
            assertThat(zipInputStream.readAllBytes()).isEqualTo(zipBytes);
        }
        assertThat(response.metadata()).containsEntry("clientMode", "local");
        assertThat(requestBody)
                .contains("name=\"files\"; filename=\"demo.pdf\"")
                .contains("name=\"response_format_zip\"")
                .contains("true")
                .contains("name=\"return_images\"");
    }

    private void respondZip(HttpExchange exchange, byte[] zipBytes) {
        try {
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            exchange.sendResponseHeaders(200, zipBytes.length);
            exchange.getResponseBody().write(zipBytes);
            exchange.close();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private byte[] zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }

    private ArtifactProcessingProperties artifactProperties() {
        ArtifactProcessingProperties properties = new ArtifactProcessingProperties();
        properties.setMaxWorkspaceBytes(1024L);
        return properties;
    }

    /**
     * 用于验证客户端未通过 readAllBytes 聚合上传文件的输入流。
     */
    private static final class ReadAllBytesFailingInputStream extends ByteArrayInputStream {

        private ReadAllBytesFailingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public byte[] readAllBytes() {
            throw new AssertionError("不应聚合读取上传文件");
        }
    }
}
