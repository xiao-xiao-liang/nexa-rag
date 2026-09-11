package com.nexarag.infra.source.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.config.CloudDocumentProperties;
import com.nexarag.infra.constants.ParsedContentTypes;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.infra.messaging.document.DocumentPipelineNonRetryableException;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import com.nexarag.infra.parser.workspace.BoundedFileTransfer;
import com.nexarag.infra.source.ExternalDocumentSourceReader;
import com.nexarag.infra.source.model.SourceReadRequestDTO;
import com.nexarag.infra.source.model.SourceReadResultBO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * 飞书 Docx 来源读取器，使用官方导出任务 API 将 DOCX 流式下载到任务工作区。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuDocxSourceReader implements ExternalDocumentSourceReader {

    private static final int FEISHU_PERMISSION_DENIED_CODE = 99991672;
    private static final String FEISHU_ROOT_DOMAIN = "feishu.cn";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final CloudDocumentProperties cloudDocumentProperties;
    private final ArtifactProcessingProperties artifactProcessingProperties;
    private final BoundedFileTransfer boundedFileTransfer;
    private final FeishuBlockMarkdownConverter blockMarkdownConverter;
    private final FeishuBlockMediaDownloader blockMediaDownloader;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(ExternalDocumentSourceType sourceType) {
        return sourceType == ExternalDocumentSourceType.FEISHU;
    }

    /**
     * 校验飞书 Docx 或 Wiki 地址并返回末级令牌。
     */
    @Override
    public String validateAndExtractDocumentId(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl);
            String[] segments = uri.getPath().split("/");
            if (!isFeishuHost(uri.getHost()) || segments.length < 3
                    || (!"docx".equals(segments[segments.length - 2])
                    && !"wiki".equals(segments[segments.length - 2])) || segments[segments.length - 1].isBlank()) {
                throw new ServiceException("飞书来源URL必须指向Docx文档或Wiki节点");
            }
            return segments[segments.length - 1];
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("飞书来源URL格式错误");
        }
    }

    /**
     * 优先从 Docx Block API 生成 Markdown，无法保真或超过边界时回退 DOCX 导出。
     */
    @Override
    public SourceReadResultBO read(SourceReadRequestDTO request, ArtifactWorkspace workspace) {
        // 1. 校验应用身份和工作区。
        validateRequest(request, workspace);
        RestClient client = RestClient.builder().baseUrl(cloudDocumentProperties.getFeishu().getBaseUrl()).build();
        String accessToken = requestTenantAccessToken(client);
        String documentId = resolveDocxDocumentId(client, accessToken, request.sourceUrl(),
                validateAndExtractDocumentId(request.sourceUrl()));

        // 2. 查询文档信息，优先按原生 Block 结构生成 Markdown。
        JsonNode document = requiredResponse(client.get().uri("/open-apis/docx/v1/documents/{documentId}", documentId)
                .header(AUTHORIZATION, "Bearer " + accessToken).retrieve().body(JsonNode.class), documentId)
                .path("data").path("document");
        BlockReadAttempt blockReadAttempt = tryReadMarkdownFromBlocks(client, accessToken, documentId,
                request.documentId());
        if (blockReadAttempt.success()) {
            MediaResolution mediaResolution = resolveMedia(accessToken, documentId, blockReadAttempt.blocks(), workspace);
            String markdown = blockMarkdownConverter.convert(blockReadAttempt.blocks(), mediaResolution.results());
            return readAsBlockMarkdown(markdown, document, documentId, request.documentId(), workspace,
                    mediaResolution.metadata());
        }

        // 3. Block 路径不可用时保留原有 DOCX/Pandoc 链路。
        log.info("飞书Block读取回退DOCX导出，documentId={}，externalDocumentId={}，reason={}",
                request.documentId(), documentId, blockReadAttempt.fallbackReason());
        return readAsDocxExport(client, accessToken, document, documentId, request.documentId(), workspace,
                blockReadAttempt.fallbackReason());
    }

    /**
     * 分页读取受限 Block 集合，并转换为 Markdown。
     */
    private BlockReadAttempt tryReadMarkdownFromBlocks(RestClient client, String accessToken,
                                                       String externalDocumentId, Long documentId) {
        CloudDocumentProperties.FeishuProperties.BlockReadProperties properties =
                cloudDocumentProperties.getFeishu().getBlockRead();
        if (properties == null || !properties.isEnabled()) {
            return BlockReadAttempt.fallback("BLOCK_READ_DISABLED");
        }
        validateBlockReadProperties(properties);

        List<JsonNode> blocks = new ArrayList<>();
        Set<String> pageTokens = new HashSet<>();
        long jsonBytes = 0L;
        String pageToken = null;
        try {
            while (true) {
                JsonNode response = getBlocksPage(client, accessToken, externalDocumentId,
                        properties.getPageSize(), pageToken);
                JsonNode data = response.path("data");
                JsonNode items = data.path("items");
                if (!items.isArray()) {
                    throw new ServiceException("飞书Block响应缺少items，documentId=" + documentId,
                            BaseErrorCode.REMOTE_ERROR);
                }

                for (JsonNode block : items) {
                    byte[] blockJson = objectMapper.writeValueAsBytes(block);
                    if (jsonBytes > properties.getMaxJsonBytes() - blockJson.length) {
                        return BlockReadAttempt.fallback("BLOCK_JSON_LIMIT_EXCEEDED");
                    }
                    jsonBytes += blockJson.length;
                    blocks.add(block);
                    if (blocks.size() > properties.getMaxBlockCount()) {
                        return BlockReadAttempt.fallback("BLOCK_COUNT_LIMIT_EXCEEDED");
                    }
                }
                if (!data.path("has_more").asBoolean(false)) {
                    break;
                }
                String nextPageToken = data.path("page_token").asText(null);
                if (!StringUtils.hasText(nextPageToken) || !pageTokens.add(nextPageToken)) {
                    return BlockReadAttempt.fallback("INVALID_BLOCK_PAGINATION");
                }
                pageToken = nextPageToken;
            }
            return blocks.isEmpty() ? BlockReadAttempt.fallback("EMPTY_BLOCK_MARKDOWN") : BlockReadAttempt.success(blocks);
        } catch (Exception exception) {
            log.warn("飞书Block API读取失败，将回退DOCX导出，documentId={}，externalDocumentId={}",
                    documentId, externalDocumentId, exception);
            return BlockReadAttempt.fallback("BLOCK_API_UNAVAILABLE");
        }
    }

    /**
     * 请求单页 Docx Block 列表。
     */
    private JsonNode getBlocksPage(RestClient client, String accessToken, String documentId,
                                   int pageSize, String pageToken) {
        JsonNode response = client.get().uri(uriBuilder -> {
                    uriBuilder.path("/open-apis/docx/v1/documents/{documentId}/blocks")
                            .queryParam("page_size", pageSize);
                    if (StringUtils.hasText(pageToken)) {
                        uriBuilder.queryParam("page_token", pageToken);
                    }
                    return uriBuilder.build(documentId);
                })
                .header(AUTHORIZATION, "Bearer " + accessToken)
                .retrieve().body(JsonNode.class);
        return requiredResponse(response, documentId);
    }

    /**
     * 将 Block 转换结果写入工作区。
     */
    private SourceReadResultBO readAsBlockMarkdown(String markdown, JsonNode document, String externalDocumentId,
                                                   Long documentId, ArtifactWorkspace workspace,
                                                   Map<String, Object> mediaMetadata) {
        byte[] content = markdown.getBytes(StandardCharsets.UTF_8);
        long maxWorkspaceBytes = artifactProcessingProperties.getMaxWorkspaceBytes();
        if (maxWorkspaceBytes <= 0) {
            throw new ServiceException("文档解析工作区大小限制必须大于零");
        }
        if (content.length > maxWorkspaceBytes) {
            throw new ServiceException("飞书Block Markdown超过工作区大小限制，documentId=" + documentId,
                    BaseErrorCode.SERVICE_ERROR);
        }
        Path sourcePath = workspace.resolve("source.md");
        try {
            Files.createDirectories(sourcePath.getParent());
            Files.write(sourcePath, content);
        } catch (Exception exception) {
            throw new ServiceException("写入飞书Block Markdown失败，documentId=" + documentId, exception,
                    BaseErrorCode.SERVICE_ERROR);
        }
        log.info("飞书Block Markdown生成成功，documentId={}，externalDocumentId={}，revisionId={}", documentId,
                externalDocumentId, document.path("revision_id").asText(null));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceType", "FEISHU");
        metadata.put("reader", "FEISHU_BLOCK_API");
        metadata.put("media", mediaMetadata);
        return new SourceReadResultBO(sourcePath, ParsedContentTypes.TEXT_MARKDOWN, DocumentFormat.MARKDOWN,
                "source.md", document.path("title").asText(null), externalDocumentId,
                document.path("revision_id").asText(null),
                Map.copyOf(metadata));
    }

    /**
     * 逐项下载媒体资源。单项失败以占位符和元数据保留，不影响 Block Markdown 主链路。
     */
    private MediaResolution resolveMedia(String accessToken, String externalDocumentId, List<JsonNode> blocks,
                                         ArtifactWorkspace workspace) {
        CloudDocumentProperties.FeishuProperties.BlockMediaProperties properties =
                cloudDocumentProperties.getFeishu().getBlockMedia();
        long totalLimit = properties == null ? 0L : properties.getMaxTotalAssetBytes();
        long downloadedBytes = 0L;
        int discoveredCount = 0;
        int downloadedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        Map<String, FeishuBlockMediaDownloadResultBO> results = new LinkedHashMap<>();
        List<Map<String, String>> failureDetails = new ArrayList<>();
        Set<String> visitedBlockIds = new HashSet<>();
        for (JsonNode block : blocks) {
            int blockType = block.path("block_type").asInt();
            if (!isMediaBlock(blockType)) {
                continue;
            }
            String blockId = block.path("block_id").asText();
            if (!StringUtils.hasText(blockId) || !visitedBlockIds.add(blockId)) {
                continue;
            }
            discoveredCount++;
            String mediaToken = extractMediaToken(block, blockType);
            long remainingBytes = totalLimit - downloadedBytes;
            FeishuBlockMediaDownloadResultBO result = blockMediaDownloader.download(accessToken, externalDocumentId,
                    blockId, blockType, mediaToken, workspace.root(), remainingBytes);
            results.put(blockId, result);
            if (result.status() == FeishuBlockMediaDownloadResultBO.Status.DOWNLOADED) {
                downloadedCount++;
                downloadedBytes += result.size();
            } else if (result.status() == FeishuBlockMediaDownloadResultBO.Status.SKIPPED) {
                skippedCount++;
                addFailureDetail(failureDetails, properties, result);
            } else {
                failedCount++;
                addFailureDetail(failureDetails, properties, result);
            }
            log.info("飞书Block媒体处理完成，externalDocumentId={}，blockId={}，blockType={}，status={}，size={}，reason={}",
                    externalDocumentId, blockId, blockType, result.status(), result.size(), result.failureReason());
        }
        Map<String, Object> metadata = Map.of("discoveredCount", discoveredCount, "downloadedCount", downloadedCount,
                "failedCount", failedCount, "skippedCount", skippedCount, "failureDetails", List.copyOf(failureDetails));
        return new MediaResolution(Map.copyOf(results), metadata);
    }

    private boolean isMediaBlock(int blockType) {
        return blockType == 23 || blockType == 27;
    }

    private String extractMediaToken(JsonNode block, int blockType) {
        JsonNode media = blockType == 27 ? block.path("image") : block.path("file");
        String token = media.path("token").asText();
        return StringUtils.hasText(token) ? token : block.path("token").asText();
    }

    private void addFailureDetail(List<Map<String, String>> failureDetails,
                                  CloudDocumentProperties.FeishuProperties.BlockMediaProperties properties,
                                  FeishuBlockMediaDownloadResultBO result) {
        int maxFailureDetails = properties == null ? 0 : properties.getMaxFailureDetails();
        if (maxFailureDetails > 0 && failureDetails.size() < maxFailureDetails) {
            failureDetails.add(Map.of("blockId", result.blockId(), "blockType", String.valueOf(result.blockType()),
                    "status", result.status().name(), "reason", result.failureReason()));
        }
    }

    /**
     * 创建 DOCX 导出任务、轮询其状态，并将结果流式下载到工作区。
     */
    private SourceReadResultBO readAsDocxExport(RestClient client, String accessToken, JsonNode document,
                                                String externalDocumentId, Long documentId,
                                                ArtifactWorkspace workspace, String fallbackReason) {
        String ticket = createExportTask(client, accessToken, externalDocumentId);
        String fileToken = awaitExportedFileToken(client, accessToken, ticket, externalDocumentId, documentId);

        Path sourcePath = workspace.resolve("source.docx");
        downloadToFile(accessToken, fileToken, sourcePath, documentId);
        log.info("飞书DOCX导出成功，documentId={}，externalDocumentId={}，revisionId={}", documentId,
                externalDocumentId, document.path("revision_id").asText(null));
        return new SourceReadResultBO(sourcePath, DOCX_CONTENT_TYPE, DocumentFormat.WORD, "source.docx",
                document.path("title").asText(null), externalDocumentId,
                document.path("revision_id").asText(null),
                Map.of("sourceType", "FEISHU", "reader", "FEISHU_EXPORT_TASK",
                        "blockFallbackReason", fallbackReason));
    }

    /**
     * 获取 tenant access token。
     */
    private String requestTenantAccessToken(RestClient client) {
        JsonNode response = client.post().uri("/open-apis/auth/v3/tenant_access_token/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("app_id", cloudDocumentProperties.getFeishu().getAppId(), "app_secret",
                        cloudDocumentProperties.getFeishu().getAppSecret()))
                .retrieve().body(JsonNode.class);
        String accessToken = response == null ? null : response.path("tenant_access_token").asText();
        if (!StringUtils.hasText(accessToken)) {
            throw new ServiceException("飞书未返回租户访问令牌", BaseErrorCode.REMOTE_ERROR);
        }
        return accessToken;
    }

    /**
     * 创建官方 DOCX 导出任务。
     */
    private String createExportTask(RestClient client, String accessToken, String documentId) {
        JsonNode response = requiredResponse(client.post().uri("/open-apis/drive/v1/export_tasks")
                .header(AUTHORIZATION, "Bearer " + accessToken).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("file_extension", "docx", "token", documentId, "type", "docx"))
                .retrieve().body(JsonNode.class), documentId);
        String ticket = response.path("data").path("ticket").asText();
        if (!StringUtils.hasText(ticket)) {
            throw new ServiceException("飞书未返回导出任务票据，documentId=" + documentId, BaseErrorCode.REMOTE_ERROR);
        }
        return ticket;
    }

    /**
     * 轮询导出任务，直到成功、失败或超过等待边界。
     */
    private String awaitExportedFileToken(RestClient client, String accessToken, String ticket,
                                          String externalDocumentId, Long documentId) {
        CloudDocumentProperties.FeishuProperties.ExportProperties properties =
                cloudDocumentProperties.getFeishu().getExport();
        validateExportProperties(properties);
        long deadlineNanos = System.nanoTime() + properties.getTimeout().toNanos();
        for (int pollCount = 0; pollCount < properties.getMaxPollCount() && System.nanoTime() < deadlineNanos; pollCount++) {
            JsonNode response = requiredResponse(client.get().uri(uriBuilder -> uriBuilder
                            .path("/open-apis/drive/v1/export_tasks/{ticket}").queryParam("token", externalDocumentId)
                            .build(ticket))
                    .header(AUTHORIZATION, "Bearer " + accessToken).retrieve().body(JsonNode.class), documentId.toString());
            JsonNode result = response.path("data").path("result");
            String fileToken = result.path("file_token").asText();
            if (StringUtils.hasText(fileToken)) {
                return fileToken;
            }
            String status = response.path("data").path("job_status").asText();
            if ("failed".equalsIgnoreCase(status)) {
                throw new ServiceException("飞书DOCX导出任务失败，documentId=" + documentId,
                        BaseErrorCode.REMOTE_ERROR);
            }
            sleep(properties.getPollInterval(), documentId);
        }
        throw new ServiceException("飞书DOCX导出任务超时，documentId=" + documentId,
                BaseErrorCode.SERVICE_TIMEOUT_ERROR);
    }

    /**
     * 使用 JDK HTTP 客户端读取下载响应流，避免 RestClient 将响应体聚合到内存。
     */
    private void downloadToFile(String accessToken, String fileToken, Path sourcePath, Long documentId) {
        if (artifactProcessingProperties.getMaxWorkspaceBytes() <= 0) {
            throw new ServiceException("文档解析工作区大小限制必须大于零");
        }
        HttpURLConnection connection = null;
        try {
            Files.createDirectories(sourcePath.getParent());
            URL url = URI.create(cloudDocumentProperties.getFeishu().getBaseUrl()
                    + "/open-apis/drive/v1/export_tasks/file/" + fileToken + "/download").toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty(AUTHORIZATION, "Bearer " + accessToken);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(toTimeoutMillis(cloudDocumentProperties.getFeishu().getExport().getTimeout()));
            connection.setInstanceFollowRedirects(true);
            if (connection.getResponseCode() / 100 != 2) {
                throw new ServiceException("下载飞书DOCX失败，documentId=" + documentId + "，httpStatus="
                        + connection.getResponseCode(), BaseErrorCode.REMOTE_ERROR);
            }
            try (InputStream inputStream = connection.getInputStream()) {
                boundedFileTransfer.copy(inputStream, sourcePath, artifactProcessingProperties.getMaxWorkspaceBytes());
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("下载飞书DOCX失败，documentId=" + documentId, exception,
                    BaseErrorCode.REMOTE_ERROR);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Wiki 节点先解析为实际对象，仅接受最终对象为 Docx。
     */
    private String resolveDocxDocumentId(RestClient client, String accessToken, String sourceUrl, String sourceToken) {
        if (!sourceUrl.contains("/wiki/")) {
            return sourceToken;
        }
        JsonNode response = client.get().uri(uriBuilder -> uriBuilder.path("/open-apis/wiki/v2/spaces/get_node")
                        .queryParam("token", sourceToken).build())
                .header(AUTHORIZATION, "Bearer " + accessToken).retrieve().body(JsonNode.class);
        JsonNode node = requiredResponse(response, sourceToken).path("data").path("node");
        if (!"docx".equals(node.path("obj_type").asText()) || !StringUtils.hasText(node.path("obj_token").asText())) {
            throw new DocumentPipelineNonRetryableException("飞书Wiki节点未指向Docx文档");
        }
        return node.path("obj_token").asText();
    }

    /**
     * 检查飞书业务响应。
     */
    private JsonNode requiredResponse(JsonNode response, String documentId) {
        if (response == null) {
            throw new ServiceException("读取飞书响应失败，documentId=" + documentId, BaseErrorCode.REMOTE_ERROR);
        }
        if (response.path("code").asInt() == FEISHU_PERMISSION_DENIED_CODE) {
            throw new DocumentPipelineNonRetryableException("飞书应用未开通读取或导出文档所需权限");
        }
        if (response.path("code").asInt() != 0) {
            throw new ServiceException("飞书接口返回业务失败，documentId=" + documentId,
                    BaseErrorCode.REMOTE_ERROR);
        }
        return response;
    }

    private void validateRequest(SourceReadRequestDTO request, ArtifactWorkspace workspace) {
        if (request == null || request.documentId() == null || !StringUtils.hasText(request.sourceUrl()) || workspace == null) {
            throw new ServiceException("飞书来源读取请求不完整");
        }
        if (!StringUtils.hasText(cloudDocumentProperties.getFeishu().getAppId())
                || !StringUtils.hasText(cloudDocumentProperties.getFeishu().getAppSecret())) {
            throw new DocumentPipelineNonRetryableException("未配置飞书应用身份凭据");
        }
    }

    private void validateExportProperties(CloudDocumentProperties.FeishuProperties.ExportProperties properties) {
        if (properties == null || properties.getTimeout() == null || properties.getTimeout().isZero()
                || properties.getTimeout().isNegative() || properties.getPollInterval() == null
                || properties.getPollInterval().isNegative() || properties.getPollInterval().isZero()
                || properties.getMaxPollCount() <= 0) {
            throw new ServiceException("飞书DOCX导出任务配置不合法");
        }
    }

    private void validateBlockReadProperties(
            CloudDocumentProperties.FeishuProperties.BlockReadProperties properties) {
        if (properties.getPageSize() <= 0 || properties.getMaxBlockCount() <= 0
                || properties.getMaxJsonBytes() <= 0) {
            throw new ServiceException("飞书Block读取配置不合法");
        }
    }

    private void sleep(Duration duration, Long documentId) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException("等待飞书DOCX导出任务被中断，documentId=" + documentId, exception,
                    BaseErrorCode.SERVICE_ERROR);
        }
    }

    private int toTimeoutMillis(Duration duration) {
        return Math.toIntExact(Math.min(duration.toMillis(), Integer.MAX_VALUE));
    }

    private boolean isFeishuHost(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return FEISHU_ROOT_DOMAIN.equals(normalizedHost) || normalizedHost.endsWith("." + FEISHU_ROOT_DOMAIN);
    }

    /**
     * Block 读取结果，成功时携带 Markdown，失败时携带可观测的回退原因。
     */
    private record BlockReadAttempt(boolean success, List<JsonNode> blocks, String fallbackReason) {

        private static BlockReadAttempt success(List<JsonNode> blocks) {
            return new BlockReadAttempt(true, List.copyOf(blocks), null);
        }

        private static BlockReadAttempt fallback(String fallbackReason) {
            return new BlockReadAttempt(false, List.of(), fallbackReason);
        }
    }

    /** Block 媒体处理结果与用于来源元数据的汇总信息。 */
    private record MediaResolution(Map<String, FeishuBlockMediaDownloadResultBO> results,
                                   Map<String, Object> metadata) {
    }
}
