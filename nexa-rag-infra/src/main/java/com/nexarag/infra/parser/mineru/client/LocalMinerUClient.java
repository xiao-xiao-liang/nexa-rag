package com.nexarag.infra.parser.mineru.client;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.config.MinerUProperties;
import com.nexarag.infra.messaging.document.DocumentPipelineNonRetryableException;
import com.nexarag.infra.parser.model.MinerUParseCommand;
import com.nexarag.infra.parser.model.MinerUParseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * 本地部署 MinerU 客户端，使用分块 multipart 上传和临时 ZIP 文件避免堆内存聚合。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.parser.mineru", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalMinerUClient implements MinerUClient {

    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_ERROR_BODY_BYTES = 4096;

    private final MinerUProperties properties;
    private final ArtifactProcessingProperties artifactProcessingProperties;

    /**
     * 调用本地 MinerU 文件解析接口。
     *
     * @param command 解析命令
     * @return 指向临时 ZIP 文件的自动清理输入流
     */
    @Override
    public MinerUParseResponse parse(MinerUParseCommand command) {
        validateCommand(command);
        Path zipFile = null;
        HttpURLConnection connection = null;
        try {
            // 1. 以分块 multipart 请求流式上传原始文件。
            String boundary = "----NexaRagMinerU" + UUID.randomUUID();
            connection = openConnection(boundary);
            try (OutputStream outputStream = new BufferedOutputStream(connection.getOutputStream())) {
                writeMultipartRequest(outputStream, boundary, command);
            }

            // 2. 将 ZIP 响应流式写入临时文件。
            int statusCode = connection.getResponseCode();
            if (statusCode / 100 != 2) {
                throw new ServiceException("调用本地MinerU失败，documentId=" + command.documentId()
                        + "，httpStatus=" + statusCode + "，responseBody=" + readErrorBody(connection),
                        BaseErrorCode.REMOTE_ERROR);
            }
            zipFile = Files.createTempFile("nexa-rag-mineru-", ".zip");
            long zipSize;
            try (InputStream inputStream = new BufferedInputStream(connection.getInputStream())) {
                zipSize = copyToFile(inputStream, zipFile, requiredMaxFileBytes());
            }
            return MinerUParseResponse.builder()
                    .zipInputStream(new DeleteOnCloseFileInputStream(zipFile))
                    .metadata(Map.of("clientMode", "local", "httpStatus", statusCode, "zipSize", zipSize))
                    .build();
        } catch (ServiceException exception) {
            deleteTemporaryFile(zipFile);
            throw exception;
        } catch (Exception exception) {
            deleteTemporaryFile(zipFile);
            throw new ServiceException("调用本地MinerU异常，documentId=" + command.documentId(), exception,
                    BaseErrorCode.REMOTE_ERROR);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 配置本地 HTTP 连接并启用分块传输。
     */
    private HttpURLConnection openConnection(String boundary) throws IOException {
        URL url = URI.create(resolveParseUrl()).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setChunkedStreamingMode(BUFFER_SIZE);
        connection.setConnectTimeout(toMillis(resolveConnectTimeout()));
        connection.setReadTimeout(toMillis(resolveReadTimeout()));
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("Accept", "application/octet-stream, application/json, */*");
        return connection;
    }

    /**
     * 将文件字段和固定表单字段逐段写入请求流。
     */
    private void writeMultipartRequest(OutputStream outputStream, String boundary, MinerUParseCommand command)
            throws IOException {
        writeTextPart(outputStream, boundary, "backend", "pipeline");
        writeTextPart(outputStream, boundary, "parse_method", command.enableOcr() ? "ocr" : "auto");
        writeTextPart(outputStream, boundary, "response_format_zip", "true");
        writeTextPart(outputStream, boundary, "return_images", "true");
        writeTextPart(outputStream, boundary, "return_model_output", "false");
        writeTextPart(outputStream, boundary, "return_middle_json", "false");
        writeFilePart(outputStream, boundary, command);
        writeAscii(outputStream, "--" + boundary + "--\r\n");
    }

    private void writeTextPart(OutputStream outputStream, String boundary, String name, String value) throws IOException {
        writeAscii(outputStream, "--" + boundary + "\r\n");
        writeAscii(outputStream, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        writeAscii(outputStream, value + "\r\n");
    }

    private void writeFilePart(OutputStream outputStream, String boundary, MinerUParseCommand command) throws IOException {
        writeAscii(outputStream, "--" + boundary + "\r\n");
        writeAscii(outputStream, "Content-Disposition: form-data; name=\"files\"; filename=\""
                + sanitizeFileName(command.fileName()) + "\"\r\n");
        writeAscii(outputStream, "Content-Type: application/octet-stream\r\n\r\n");

        try (InputStream inputStream = command.inputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        writeAscii(outputStream, "\r\n");
    }

    /**
     * 将响应复制到临时文件，并在超过工作区限制时立即清理。
     */
    private long copyToFile(InputStream inputStream, Path targetPath, long maxBytes) throws IOException {
        long copiedBytes = 0L;
        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(targetPath))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                copiedBytes = Math.addExact(copiedBytes, bytesRead);
                if (copiedBytes > maxBytes) {
                    throw new DocumentPipelineNonRetryableException("MinerU ZIP 解析产物超过大小限制，maxBytes=" + maxBytes);
                }
                outputStream.write(buffer, 0, bytesRead);
            }
            return copiedBytes;
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(targetPath);
            throw exception;
        }
    }

    private void validateCommand(MinerUParseCommand command) {
        if (command == null || command.documentId() == null || command.inputStream() == null) {
            throw new ServiceException("本地MinerU解析请求不完整");
        }
    }

    private long requiredMaxFileBytes() {
        if (artifactProcessingProperties.getMaxWorkspaceBytes() <= 0) {
            throw new ServiceException("文档解析工作区大小限制必须大于零");
        }
        return artifactProcessingProperties.getMaxWorkspaceBytes();
    }

    private String readErrorBody(HttpURLConnection connection) throws IOException {
        try (InputStream errorStream = connection.getErrorStream()) {
            if (errorStream == null) {
                return "";
            }
            return new String(errorStream.readNBytes(MAX_ERROR_BODY_BYTES), StandardCharsets.UTF_8);
        }
    }

    private void writeAscii(OutputStream outputStream, String value) throws IOException {
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException exception) {
            log.warn("删除MinerU ZIP临时文件失败，path={}", temporaryFile, exception);
        }
    }

    private String resolveParseUrl() {
        String endpoint = trimRightSlash(properties.getLocalEndpoint());
        String path = properties.getLocalParsePath();
        if (!StringUtils.hasText(path)) {
            path = "/file_parse";
        }
        return endpoint + (path.startsWith("/") ? path : "/" + path);
    }

    private String trimRightSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "http://127.0.0.1:8000";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "document";
        }
        return fileName.replace("\\", "_").replace("/", "_").replace("\"", "_");
    }

    private Duration resolveConnectTimeout() {
        return properties.getConnectTimeout() == null ? Duration.ofSeconds(3) : properties.getConnectTimeout();
    }

    private Duration resolveReadTimeout() {
        return properties.getReadTimeout() == null ? Duration.ofSeconds(120) : properties.getReadTimeout();
    }

    private int toMillis(Duration duration) {
        long millis = duration.toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }
}
