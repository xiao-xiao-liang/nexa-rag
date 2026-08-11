package com.nexarag.infra.source.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.FeishuSourceProperties;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.infra.messaging.document.DocumentPipelineNonRetryableException;
import com.nexarag.infra.source.ExternalDocumentSourceReader;
import com.nexarag.infra.source.model.SourceReadRequestDTO;
import com.nexarag.infra.source.model.SourceReadResultBO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * 通过飞书开放平台应用身份读取单篇 Docx 文档。
 */
@Component
@RequiredArgsConstructor
public class FeishuDocxSourceReader implements ExternalDocumentSourceReader {

    private static final int FEISHU_PERMISSION_DENIED_CODE = 99991672;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int MAX_FAILURE_RESPONSE_LENGTH = 300;

    private final FeishuSourceProperties properties;
    private final ObjectMapper objectMapper;
    private final FeishuBlockMarkdownConverter blockMarkdownConverter;

    @Override
    public boolean supports(ExternalDocumentSourceType sourceType) {
        return sourceType == ExternalDocumentSourceType.FEISHU;
    }

    @Override
    public String validateAndExtractDocumentId(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl);
            String[] segments = uri.getPath().split("/");
            if (uri.getHost() == null || !uri.getHost().endsWith("feishu.cn") || segments.length < 3
                    || (!"docx".equals(segments[segments.length - 2])
                    && !"wiki".equals(segments[segments.length - 2])) || segments[segments.length - 1].isBlank()) {
                throw new ServiceException("飞书来源URL必须指向Docx文档或Wiki节点");
            }
            return segments[segments.length - 1];
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("飞书来源URL格式错误");
        }
    }

    @Override
    public SourceReadResultBO read(SourceReadRequestDTO request) {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppSecret())) {
            throw new DocumentPipelineNonRetryableException("未配置飞书应用身份凭据");
        }
        try {
            String sourceToken = validateAndExtractDocumentId(request.sourceUrl());
            RestClient client = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
            String accessToken = Objects.requireNonNull(client.post().uri("/open-apis/auth/v3/tenant_access_token/internal")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("app_id", properties.getAppId(), "app_secret", properties.getAppSecret()))
                    .retrieve().body(JsonNode.class)).path("tenant_access_token").asText();
            String documentId = resolveDocxDocumentId(client, accessToken, request.sourceUrl(), sourceToken);
            JsonNode documentResponse = requiredResponse(client.get().uri("/open-apis/docx/v1/documents/{documentId}", documentId)
                    .header(AUTHORIZATION, "Bearer " + accessToken).retrieve().body(JsonNode.class), documentId);
            JsonNode documentInfo = documentResponse.path("data").path("document");
            String revisionId = documentInfo.path("revision_id").asText();
            if (!StringUtils.hasText(revisionId)) {
                throw new DocumentPipelineNonRetryableException("飞书Docx未返回revisionId，documentId=" + documentId);
            }
            List<JsonNode> blocks = readAllBlocks(client, accessToken, documentId, revisionId);
            String markdown = blockMarkdownConverter.convert(blocks);
            if (!StringUtils.hasText(markdown)) {
                throw new DocumentPipelineNonRetryableException("飞书Docx未返回可解析Block，documentId=" + documentId);
            }
            return new SourceReadResultBO(toSnapshot(documentResponse, blocks), "application/json", markdown,
                    documentInfo.path("title").asText(null), documentId, revisionId, Map.of("sourceType", "FEISHU"));
        } catch (HttpClientErrorException exception) {
            throw mapClientException(exception);
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
        assert response != null;
        JsonNode node = response.path("data").path("node");
        if (!"docx".equals(node.path("obj_type").asText()) || !StringUtils.hasText(node.path("obj_token").asText())) {
            throw new ServiceException("飞书Wiki节点未指向Docx文档");
        }
        return node.path("obj_token").asText();
    }

    private byte[] toBytes(JsonNode response) {
        try {
            return objectMapper.writeValueAsBytes(response);
        } catch (Exception exception) {
            throw new ServiceException("序列化飞书来源快照失败");
        }
    }

    private List<JsonNode> readAllBlocks(RestClient client, String accessToken, String documentId, String revisionId) {
        List<JsonNode> blocks = new ArrayList<>();
        String pageToken = null;
        do {
            String currentPageToken = pageToken;
            JsonNode response = requiredResponse(client.get().uri(uriBuilder -> uriBuilder
                            .path("/open-apis/docx/v1/documents/{documentId}/blocks")
                            .queryParam("page_size", 100).queryParam("document_revision_id", revisionId)
                            .queryParamIfPresent("page_token", java.util.Optional.ofNullable(currentPageToken))
                            .build(documentId))
                    .header(AUTHORIZATION, "Bearer " + accessToken).retrieve().body(JsonNode.class), documentId);
            response.path("data").path("items").forEach(blocks::add);
            pageToken = response.path("data").path("has_more").asBoolean()
                    ? response.path("data").path("page_token").asText() : null;
        } while (StringUtils.hasText(pageToken));
        return blocks;
    }

    private JsonNode requiredResponse(JsonNode response, String documentId) {
        if (response == null) {
            throw new ServiceException("读取飞书Docx失败，documentId=" + documentId);
        }
        if (response.path("code").asInt() == FEISHU_PERMISSION_DENIED_CODE) {
            throw new DocumentPipelineNonRetryableException("飞书应用未开通读取文档所需权限");
        }
        if (response.path("code").asInt() != 0) {
            throw new ServiceException("读取飞书Docx失败，documentId=" + documentId);
        }
        return response;
    }

    /**
     * 将飞书客户端错误区分为可重试和不可重试两类，避免权限错误耗尽消息重试次数。
     */
    private RuntimeException mapClientException(HttpClientErrorException exception) {
        if (exception.getStatusCode().value() == HTTP_TOO_MANY_REQUESTS) {
            return exception;
        }
        String responseBody = exception.getResponseBodyAsString();
        if (responseBody.contains(String.valueOf(FEISHU_PERMISSION_DENIED_CODE))) {
            return new DocumentPipelineNonRetryableException("飞书应用未开通Wiki节点读取权限，飞书响应="
                    + truncateResponseBody(responseBody), exception);
        }
        return new DocumentPipelineNonRetryableException("飞书请求被拒绝，httpStatus=" + exception.getStatusCode().value()
                + "，飞书响应=" + truncateResponseBody(responseBody), exception);
    }

    /**
     * 控制飞书错误响应写入文档失败原因时的长度，避免超过数据库字段限制。
     */
    private String truncateResponseBody(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "无响应体";
        }
        return responseBody.length() <= MAX_FAILURE_RESPONSE_LENGTH
                ? responseBody : responseBody.substring(0, MAX_FAILURE_RESPONSE_LENGTH);
    }

    private byte[] toSnapshot(JsonNode documentResponse, List<JsonNode> blocks) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.set("document", documentResponse);
        ArrayNode blockNodes = snapshot.putArray("blocks");
        blocks.forEach(blockNodes::add);
        return toBytes(snapshot);
    }
}
