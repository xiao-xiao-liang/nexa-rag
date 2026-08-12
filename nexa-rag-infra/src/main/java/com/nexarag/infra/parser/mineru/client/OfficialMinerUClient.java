package com.nexarag.infra.parser.mineru.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.config.MinerUProperties;
import com.nexarag.infra.parser.workspace.BoundedFileTransfer;
import com.nexarag.infra.parser.model.MinerUParseCommand;
import com.nexarag.infra.parser.model.MinerUParseResponse;
import com.nexarag.infra.parser.model.OfficialMinerUResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 官方 MinerU 客户端，负责通过官方异步接口上传文件、轮询任务并下载 ZIP 产物。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.parser.mineru", name = "mode", havingValue = "official")
public class OfficialMinerUClient implements MinerUClient {

    private static final BoundedFileTransfer FILE_TRANSFER = new BoundedFileTransfer();

    private final MinerUProperties properties;
    private final ObjectMapper objectMapper;
    private final ArtifactProcessingProperties artifactProcessingProperties;

    /**
     * 调用官方 MinerU 服务解析文件。
     *
     * @param command 解析命令
     * @return MinerU ZIP 解析响应
     */
    @Override
    public MinerUParseResponse parse(MinerUParseCommand command) {
        validateApiKey(command);
        Path zipFile = null;
        try {
            // 1. 申请签名地址后，直接将原始文件流上传至对象存储。
            ApplyResult applyResult = applyUploadUrl(command);
            try (InputStream inputStream = command.inputStream()) {
                uploadFile(command, applyResult.fileUrl(), inputStream);
            }

            // 2. 轮询官方任务，完成后将 ZIP 解析产物写入临时文件。
            PollResult pollResult = waitForResult(command, applyResult.batchId());
            DownloadedZip downloadedZip = downloadZip(command, pollResult.fullZipUrl());
            zipFile = downloadedZip.path();
            return MinerUParseResponse.builder()
                    .zipInputStream(new DeleteOnCloseFileInputStream(zipFile))
                    .metadata(Map.of(
                            "clientMode", "official",
                            "batchId", applyResult.batchId(),
                            "pollCount", pollResult.pollCount(),
                            "zipSize", Math.toIntExact(downloadedZip.size())
                    ))
                    .build();
        } catch (ServiceException exception) {
            deleteTemporaryFile(zipFile);
            throw exception;
        } catch (Exception exception) {
            deleteTemporaryFile(zipFile);
            throw new ServiceException("调用官方MinerU异常，documentId=" + command.documentId(),
                    exception, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private void validateApiKey(MinerUParseCommand command) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new ServiceException("官方MinerU API Key不能为空，documentId=" + command.documentId());
        }
    }

    private ApplyResult applyUploadUrl(MinerUParseCommand command) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "files", List.of(Map.of(
                            "name", sanitizeFileName(command.fileName()),
                            "data_id", "document-" + command.documentId())),
                    "model_version", "vlm"
            );
            // 2. 显式序列化为UTF-8字节，避免消息转换器再次改变files数组结构
            String requestJson = objectMapper.writeValueAsString(requestBody);
            HttpEntity<byte[]> request = new HttpEntity<>(requestJson.getBytes(StandardCharsets.UTF_8),
                    buildAuthorizedHeaders());
            ResponseEntity<String> response = buildRestTemplate().postForEntity(
                    resolveOfficialEndpoint() + "/api/v4/file-urls/batch",
                    request,
                    String.class
            );
            OfficialMinerUResponse body = readResponse(response.getBody(), "申请上传地址", command.documentId());
            if (!response.getStatusCode().is2xxSuccessful() || body.code() != 0) {
                throw remoteError("申请上传地址失败，官方业务码=" + body.code() + "，官方信息=" + body.msg(),
                        command.documentId());
            }
            if (body.data() == null) {
                throw remoteError("申请上传地址失败，缺少字段=data", command.documentId());
            }
            if (!StringUtils.hasText(body.data().batchId())) {
                throw remoteError("申请上传地址失败，缺少字段=batchId", command.documentId());
            }
            if (body.data().fileUrls() == null || body.data().fileUrls().isEmpty()
                    || !StringUtils.hasText(body.data().fileUrls().getFirst())) {
                throw remoteError("申请上传地址失败，缺少字段=fileUrls", command.documentId());
            }
            return new ApplyResult(body.data().batchId(), body.data().fileUrls().getFirst());
        } catch (JsonProcessingException exception) {
            throw new ServiceException("申请上传地址失败，请求体序列化异常，documentId=" + command.documentId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        } catch (HttpStatusCodeException exception) {
            throw new ServiceException("申请上传地址失败，httpStatus=" + exception.getStatusCode().value()
                    + "，documentId=" + command.documentId(), exception, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private void uploadFile(MinerUParseCommand command, String fileUrl, InputStream inputStream) {
        try {
            // 1. 签名上传地址已包含鉴权信息，不额外设置Content-Type，避免改变OSS签名串
            HttpStatusCode statusCode = buildRestTemplate().execute(
                    URI.create(fileUrl),
                    HttpMethod.PUT,
                    request -> inputStream.transferTo(request.getBody()),
                    ClientHttpResponse::getStatusCode
            );
            if (statusCode == null || !statusCode.is2xxSuccessful()) {
                int statusValue = statusCode == null ? -1 : statusCode.value();
                throw remoteError("上传原始文件失败，httpStatus=" + statusValue, command.documentId());
            }
        } catch (HttpStatusCodeException exception) {
            throw new ServiceException("上传原始文件失败，httpStatus=" + exception.getStatusCode().value()
                    + "，documentId=" + command.documentId(), exception, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private PollResult waitForResult(MinerUParseCommand command, String batchId) {
        int maxPollCount = resolveMaxPollCount();
        for (int pollCount = 1; pollCount <= maxPollCount; pollCount++) {
            OfficialMinerUResponse.ExtractResult result = queryBatchResult(command, batchId);
            if ("done".equalsIgnoreCase(result.state())) {
                if (!StringUtils.hasText(result.fullZipUrl())) {
                    throw remoteError("查询解析结果失败，完成状态缺少ZIP地址", command.documentId());
                }
                return new PollResult(result.fullZipUrl(), pollCount);
            }
            if ("failed".equalsIgnoreCase(result.state())) {
                String errorMessage = StringUtils.hasText(result.errMsg()) ? result.errMsg() : "未知错误";
                throw remoteError("官方解析任务失败，原因=" + errorMessage, command.documentId());
            }
            if (pollCount < maxPollCount) {
                sleepBeforeNextPoll(command);
            }
        }
        throw new ServiceException("官方MinerU解析任务轮询超时，documentId=" + command.documentId(),
                BaseErrorCode.SERVICE_TIMEOUT_ERROR);
    }

    private OfficialMinerUResponse.ExtractResult queryBatchResult(MinerUParseCommand command, String batchId) {
        try {
            HttpEntity<Void> request = new HttpEntity<>(buildAuthorizedHeaders());
            ResponseEntity<String> response = buildRestTemplate().exchange(
                    resolveOfficialEndpoint() + "/api/v4/extract-results/batch/" + batchId,
                    HttpMethod.GET,
                    request,
                    String.class
            );
            OfficialMinerUResponse body = readResponse(response.getBody(), "查询解析结果", command.documentId());
            if (!response.getStatusCode().is2xxSuccessful() || body.code() != 0 || body.data() == null
                    || body.data().extractResult() == null || body.data().extractResult().isEmpty()
                    || !StringUtils.hasText(body.data().extractResult().getFirst().state())) {
                throw remoteError("查询解析结果失败，官方响应缺少必要字段", command.documentId());
            }
            return body.data().extractResult().getFirst();
        } catch (HttpStatusCodeException exception) {
            throw new ServiceException("查询解析结果失败，httpStatus=" + exception.getStatusCode().value()
                    + "，documentId=" + command.documentId(), exception, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private DownloadedZip downloadZip(MinerUParseCommand command, String fullZipUrl) {
        Path zipFile = null;
        try {
            zipFile = Files.createTempFile("nexa-rag-mineru-", ".zip");
            Files.deleteIfExists(zipFile);
            Path targetZipFile = zipFile;
            Long zipSize = buildRestTemplate().execute(
                    URI.create(fullZipUrl),
                    HttpMethod.GET,
                    null,
                    response -> FILE_TRANSFER.copy(response.getBody(), targetZipFile, requiredMaxFileBytes())
            );
            if (zipSize == null || zipSize <= 0) {
                throw remoteError("下载ZIP解析产物失败", command.documentId());
            }
            return new DownloadedZip(zipFile, zipSize);
        } catch (HttpStatusCodeException exception) {
            deleteTemporaryFile(zipFile);
            throw new ServiceException("下载ZIP解析产物失败，httpStatus=" + exception.getStatusCode().value()
                    + "，documentId=" + command.documentId(), exception, BaseErrorCode.REMOTE_ERROR);
        } catch (java.io.IOException exception) {
            deleteTemporaryFile(zipFile);
            throw new ServiceException("下载ZIP解析产物失败，documentId=" + command.documentId(),
                    exception, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private OfficialMinerUResponse readResponse(String responseBody, String stage, Long documentId) {
        if (!StringUtils.hasText(responseBody)) {
            throw remoteError(stage + "失败，官方响应为空", documentId);
        }
        try {
            return objectMapper.readValue(responseBody, OfficialMinerUResponse.class);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(stage + "失败，官方响应格式错误，documentId=" + documentId,
                    exception, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private void sleepBeforeNextPoll(MinerUParseCommand command) {
        try {
            Thread.sleep(resolvePollInterval().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException("官方MinerU解析任务轮询被中断，documentId=" + command.documentId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private HttpHeaders buildAuthorizedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        return headers;
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(toMillis(resolveConnectTimeout()));
        requestFactory.setReadTimeout(toMillis(resolveReadTimeout()));
        return new RestTemplate(requestFactory);
    }

    private String resolveOfficialEndpoint() {
        String endpoint = properties.getOfficialEndpoint();
        if (!StringUtils.hasText(endpoint)) {
            return "https://mineru.net";
        }
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
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

    private Duration resolvePollInterval() {
        return properties.getPollInterval() == null ? Duration.ofSeconds(2) : properties.getPollInterval();
    }

    private int resolveMaxPollCount() {
        return properties.getMaxPollCount() > 0 ? properties.getMaxPollCount() : 60;
    }

    private int toMillis(Duration duration) {
        long millis = duration.toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    private long requiredMaxFileBytes() {
        if (artifactProcessingProperties.getMaxWorkspaceBytes() <= 0) {
            throw new ServiceException("文档解析工作区大小限制必须大于零");
        }
        return artifactProcessingProperties.getMaxWorkspaceBytes();
    }

    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (java.io.IOException exception) {
            log.warn("删除MinerU ZIP临时文件失败，path={}", temporaryFile, exception);
        }
    }

    private ServiceException remoteError(String message, Long documentId) {
        return new ServiceException(message + "，documentId=" + documentId, BaseErrorCode.REMOTE_ERROR);
    }

    private record ApplyResult(String batchId, String fileUrl) {
    }

    private record PollResult(String fullZipUrl, int pollCount) {
    }

    private record DownloadedZip(Path path, long size) {
    }
}
