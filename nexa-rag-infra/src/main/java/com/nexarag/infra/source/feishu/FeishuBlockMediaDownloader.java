package com.nexarag.infra.source.feishu;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.CloudDocumentProperties;
import com.nexarag.infra.messaging.document.DocumentPipelineNonRetryableException;
import com.nexarag.infra.parser.workspace.BoundedFileTransfer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * 飞书 Block 媒体资源下载器，以流方式写入受管工作区。
 */
@Component
@RequiredArgsConstructor
public class FeishuBlockMediaDownloader {

    private final CloudDocumentProperties cloudDocumentProperties;
    private final BoundedFileTransfer boundedFileTransfer;

    /**
     * 下载单个飞书媒体资源，下载端点无权限时尝试预览下载端点。
     *
     * @param accessToken       飞书租户访问令牌
     * @param documentId        飞书 Docx 文档 ID
     * @param blockId           来源 Block ID
     * @param blockType         来源 Block 类型
     * @param mediaToken        飞书媒体令牌
     * @param workspaceRoot     当前工作区根目录
     * @param remainingMaxBytes 本文档剩余媒体预算
     * @return 单项下载结果
     */
    public FeishuBlockMediaDownloadResultBO download(String accessToken, String documentId, String blockId,
                                                      int blockType, String mediaToken, Path workspaceRoot,
                                                      long remainingMaxBytes) {
        CloudDocumentProperties.FeishuProperties.BlockMediaProperties properties =
                cloudDocumentProperties.getFeishu().getBlockMedia();
        if (properties == null || !properties.isEnabled()) {
            return FeishuBlockMediaDownloadResultBO.unavailable(blockId, blockType,
                    FeishuBlockMediaDownloadResultBO.Status.SKIPPED, "MEDIA_DOWNLOAD_DISABLED");
        }
        if (!StringUtils.hasText(mediaToken)) {
            return FeishuBlockMediaDownloadResultBO.unavailable(blockId, blockType,
                    FeishuBlockMediaDownloadResultBO.Status.SKIPPED, "MISSING_TOKEN");
        }
        if (workspaceRoot == null || remainingMaxBytes <= 0 || properties.getMaxSingleAssetBytes() <= 0) {
            return FeishuBlockMediaDownloadResultBO.unavailable(blockId, blockType,
                    FeishuBlockMediaDownloadResultBO.Status.SKIPPED, "SINGLE_ASSET_SIZE_LIMIT_EXCEEDED");
        }
        long maxBytes = Math.min(properties.getMaxSingleAssetBytes(), remainingMaxBytes);
        try {
            return downloadWithFallback(accessToken, documentId, blockId, blockType, mediaToken, workspaceRoot, maxBytes);
        } catch (DocumentPipelineNonRetryableException exception) {
            return FeishuBlockMediaDownloadResultBO.unavailable(blockId, blockType,
                    FeishuBlockMediaDownloadResultBO.Status.SKIPPED, "SINGLE_ASSET_SIZE_LIMIT_EXCEEDED");
        } catch (Exception exception) {
            return FeishuBlockMediaDownloadResultBO.unavailable(blockId, blockType,
                    FeishuBlockMediaDownloadResultBO.Status.FAILED, "MEDIA_DOWNLOAD_FAILED");
        }
    }

    private FeishuBlockMediaDownloadResultBO downloadWithFallback(String accessToken, String documentId, String blockId,
                                                                    int blockType, String mediaToken, Path workspaceRoot,
                                                                    long maxBytes) throws Exception {
        DownloadResponse directResponse = openDownload(accessToken, documentId, mediaToken, false);
        DownloadResponse response;
        if (directResponse.statusCode() == HttpURLConnection.HTTP_UNAUTHORIZED
                || directResponse.statusCode() == HttpURLConnection.HTTP_FORBIDDEN
                || directResponse.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            directResponse.close();
            response = openDownload(accessToken, documentId, mediaToken, true);
        } else {
            response = directResponse;
        }
        try (response) {
            if (response.statusCode() / 100 != 2) {
                return FeishuBlockMediaDownloadResultBO.unavailable(blockId, blockType,
                        FeishuBlockMediaDownloadResultBO.Status.FAILED, "HTTP_" + response.statusCode());
            }
            long contentLength = response.connection().getContentLengthLong();
            if (contentLength > maxBytes) {
                return FeishuBlockMediaDownloadResultBO.unavailable(blockId, blockType,
                        FeishuBlockMediaDownloadResultBO.Status.SKIPPED, "SINGLE_ASSET_SIZE_LIMIT_EXCEEDED");
            }
            String contentType = normalizeContentType(response.connection().getContentType());
            Path assetsDirectory = workspaceRoot.resolve("assets");
            Files.createDirectories(assetsDirectory);
            Path target = assetsDirectory.resolve(safeFileName(blockId) + extensionFor(contentType));
            try (InputStream inputStream = response.connection().getInputStream()) {
                long size = boundedFileTransfer.copy(inputStream, target, maxBytes);
                return FeishuBlockMediaDownloadResultBO.downloaded(blockId, blockType,
                        "assets/" + target.getFileName(), target, contentType, size);
            }
        }
    }

    private DownloadResponse openDownload(String accessToken, String documentId, String mediaToken,
                                          boolean preview) throws Exception {
        String endpoint = "/open-apis/drive/v1/medias/" + URLEncoder.encode(mediaToken, StandardCharsets.UTF_8)
                + (preview ? "/preview_download" : "/download");
        String query = preview ? "" : "?extra=" + URLEncoder.encode("{\"doc_id\":\"" + documentId
                + "\",\"doc_type\":\"docx\"}", StandardCharsets.UTF_8);
        URL url = URI.create(cloudDocumentProperties.getFeishu().getBaseUrl() + endpoint + query).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty(AUTHORIZATION, "Bearer " + accessToken);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        return new DownloadResponse(connection, connection.getResponseCode());
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/svg+xml" -> ".svg";
            case "application/pdf" -> ".pdf";
            default -> ".png";
        };
    }

    private String safeFileName(String blockId) {
        String normalized = blockId == null ? "media" : blockId.replaceAll("[^A-Za-z0-9._-]", "_");
        return StringUtils.hasText(normalized) ? normalized : "media";
    }

    /** 下载连接资源释放包装。 */
    private record DownloadResponse(HttpURLConnection connection, int statusCode) implements AutoCloseable {

        @Override
        public void close() {
            connection.disconnect();
        }
    }
}
