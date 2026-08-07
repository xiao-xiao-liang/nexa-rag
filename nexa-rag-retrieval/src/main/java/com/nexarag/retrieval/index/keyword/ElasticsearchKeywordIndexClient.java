package com.nexarag.retrieval.index.keyword;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.dto.req.KeywordIndexWriteRequest;
import com.nexarag.retrieval.dto.req.KeywordIndexSearchRequest;
import com.nexarag.retrieval.model.KeywordIndexDocument;
import com.nexarag.retrieval.model.KeywordIndexSearchResult;
import com.nexarag.retrieval.model.KeywordIndexWriteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nexarag.retrieval.constants.DocumentIndexFieldConstants.*;
import static com.nexarag.retrieval.constants.ElasticsearchIndexConstants.MAX_RESPONSE_BODY_LENGTH;

/**
 * Elasticsearch 关键词索引客户端，负责创建片段关键词索引、写入片段文本并按文档清理索引数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.retrieval.keyword", name = "type", havingValue = "elasticsearch")
public class ElasticsearchKeywordIndexClient implements KeywordIndexClient {

    private final RetrievalProperties retrievalProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * 批量写入或更新 Elasticsearch 关键词索引。
     *
     * @param request 关键词索引写入请求
     * @return 写入结果列表
     */
    @Override
    public List<KeywordIndexWriteResult> upsert(KeywordIndexWriteRequest request) {
        if (request == null || request.documents() == null || request.documents().isEmpty()) {
            return List.of();
        }

        // 1. 写入前确认索引存在，不存在时按片段检索字段创建
        String indexName = resolveIndexName(request.indexName());
        ensureIndex(indexName);

        // 2. 逐片段写入关键词索引，返回稳定索引ID供数据库回写
        return request.documents().stream()
                .map(document -> upsertDocument(indexName, document))
                .toList();
    }

    /**
     * 使用 Elasticsearch BM25 检索片段。
     *
     * @param request 关键词检索请求
     * @return 按相关性排序的片段结果
     */
    @Override
    public List<KeywordIndexSearchResult> search(KeywordIndexSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.query()) || request.topK() <= 0) {
            return List.of();
        }

        // 1. 同时检索新索引字段和历史正文，兼容未完成重建的旧文档
        String indexName = resolveIndexName(request.indexName());
        Map<String, Object> body = Map.of(
                "size", request.topK(),
                "query", Map.of("bool", Map.of(
                        "should", List.of(
                                Map.of("match", Map.of(INDEX_CONTENT, request.query())),
                                Map.of("match", Map.of(TEXT, request.query()))),
                        "minimum_should_match", 1)));
        HttpResponse<String> response = send(jsonRequest("POST", "/" + encodePath(indexName) + "/_search", toJson(body)));
        validateSuccess(response, "Elasticsearch 关键词检索失败");

        // 2. 将 Elasticsearch 文档和分数转换为模块内标准结果
        return parseSearchResults(response.body());
    }

    /**
     * 按文档ID删除 Elasticsearch 关键词索引。
     *
     * @param documentId 文档ID
     * @return 删除数量
     */
    @Override
    public int deleteByDocumentId(Long documentId) {
        return deleteByDocumentId(documentId, null);
    }

    /**
     * 按文档ID删除指定 Elasticsearch 索引中的记录。
     *
     * @param documentId 文档ID
     * @param indexName  索引名称，为空时使用默认正文索引
     * @return 删除数量
     */
    @Override
    public int deleteByDocumentId(Long documentId, String indexName) {
        if (documentId == null) {
            return 0;
        }

        // 1. 使用 delete_by_query 按稳定文档ID清理片段索引
        String resolvedIndexName = resolveIndexName(indexName);
        Map<String, Object> termValue = Map.of("value", documentId);
        Map<String, Object> body = Map.of("query",
                Map.of("term", Map.of(DOCUMENT_ID, termValue)));
        HttpResponse<String> response = send(jsonRequest("POST", "/" + encodePath(resolvedIndexName) + "/_delete_by_query",
                toJson(body)));
        validateSuccess(response, "Elasticsearch 关键词索引清理失败");
        int deletedCount = parseDeletedCount(response.body());
        log.info("Elasticsearch 关键词索引清理完成，documentId={}，indexName={}，deletedCount={}",
                documentId, resolvedIndexName, deletedCount);
        return deletedCount;
    }

    private KeywordIndexWriteResult upsertDocument(String indexName, KeywordIndexDocument document) {
        HttpResponse<String> response = send(jsonRequest("PUT",
                "/" + encodePath(indexName) + "/_doc/" + encodePath(document.chunkId()),
                toJson(toElasticsearchDocument(document))));
        validateSuccess(response, "Elasticsearch 关键词索引写入失败");
        return new KeywordIndexWriteResult(document.chunkId(), keywordIndexId(indexName, document.chunkId()),
                true, null);
    }

    private void ensureIndex(String indexName) {
        HttpResponse<String> headResponse = send(request("HEAD", "/" + encodePath(indexName), null));
        if (isSuccess(headResponse.statusCode())) {
            ensureSectionFieldMapping(indexName);
            return;
        }
        if (headResponse.statusCode() != 404) {
            throw new ServiceException("Elasticsearch 关键词索引检查失败，indexName=" + indexName
                    + "，statusCode=" + headResponse.statusCode());
        }

        // 1. 索引不存在时创建基础映射，保证精确字段和全文字段类型稳定
        HttpResponse<String> createResponse = send(jsonRequest("PUT", "/" + encodePath(indexName), mappingJson()));
        validateSuccess(createResponse, "Elasticsearch 关键词索引创建失败");
        log.info("Elasticsearch 关键词索引创建完成，indexName={}", indexName);
    }

    /**
     * 为既有关键词索引补充章节检索字段映射，不改变历史正文数据。
     *
     * @param indexName 索引名称
     */
    private void ensureSectionFieldMapping(String indexName) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(SECTION_ID, Map.of("type", "long"));
        properties.put(INDEX_CONTENT, Map.of("type", "text"));
        HttpResponse<String> response = send(jsonRequest("PUT", "/" + encodePath(indexName) + "/_mapping",
                toJson(Map.of("properties", properties))));
        validateSuccess(response, "Elasticsearch 关键词索引映射更新失败");
        log.info("Elasticsearch 关键词索引章节字段映射已确认，indexName={}", indexName);
    }

    private Map<String, Object> toElasticsearchDocument(KeywordIndexDocument document) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(CHUNK_ID, document.chunkId());
        body.put(DOCUMENT_ID, document.documentId());
        body.put(PARENT_CHUNK_ID, document.parentChunkId());
        body.put(CHUNK_ORDER, document.chunkOrder());
        body.put(SECTION_ID, document.sectionId());
        body.put(TEXT, document.text());
        body.put(INDEX_CONTENT, document.indexContent());
        body.put(METADATA_JSON, document.metadataJson());
        return body;
    }

    private String mappingJson() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(CHUNK_ID, Map.of("type", "keyword"));
        properties.put(DOCUMENT_ID, Map.of("type", "long"));
        properties.put(PARENT_CHUNK_ID, Map.of("type", "keyword"));
        properties.put(CHUNK_ORDER, Map.of("type", "integer"));
        properties.put(SECTION_ID, Map.of("type", "long"));
        properties.put(TEXT, Map.of("type", "text"));
        properties.put(INDEX_CONTENT, Map.of("type", "text"));
        properties.put(METADATA_JSON, Map.of("type", "keyword", "index", false));
        return toJson(Map.of("mappings", Map.of("properties", properties)));
    }

    private HttpRequest.Builder jsonRequest(String method, String path, String body) {
        return request(method, path, body).header("Content-Type", "application/json");
    }

    private HttpRequest.Builder request(String method, String path, String body) {
        RetrievalProperties.Keyword keywordProperties = retrievalProperties.getKeyword();
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .timeout(Duration.ofMillis(keywordProperties.getRequestTimeoutMs()))
                .method(method, publisher);
        if (StringUtils.hasText(keywordProperties.getUsername())) {
            builder.header("Authorization", basicAuthorization(keywordProperties));
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest.Builder requestBuilder) {
        try {
            return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new ServiceException("Elasticsearch 关键词索引请求失败", exception, BaseErrorCode.SERVICE_ERROR);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Elasticsearch 关键词索引请求被中断", exception,
                    BaseErrorCode.SERVICE_ERROR);
        }
    }

    private void validateSuccess(HttpResponse<String> response, String message) {
        if (!isSuccess(response.statusCode())) {
            throw new ServiceException(message + "，statusCode=" + response.statusCode()
                    + "，responseBody=" + truncateResponseBody(response.body()));
        }
    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private int parseDeletedCount(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return root.path("deleted").asInt(0);
        } catch (IOException exception) {
            throw new ServiceException("解析 Elasticsearch 删除结果失败", exception,
                    BaseErrorCode.SERVICE_ERROR);
        }
    }

    private List<KeywordIndexSearchResult> parseSearchResults(String body) {
        try {
            JsonNode hits = objectMapper.readTree(body).path("hits").path("hits");
            if (!hits.isArray()) {
                return List.of();
            }
            List<KeywordIndexSearchResult> results = new java.util.ArrayList<>();
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                results.add(new KeywordIndexSearchResult(
                        source.path(CHUNK_ID).asText(null),
                        source.path(DOCUMENT_ID).isNumber() ? source.path(DOCUMENT_ID).asLong() : null,
                        source.path(PARENT_CHUNK_ID).asText(null),
                        source.path(CHUNK_ORDER).isInt() ? source.path(CHUNK_ORDER).asInt() : null,
                        source.path(SECTION_ID).isNumber() ? source.path(SECTION_ID).asLong() : null,
                        source.path(TEXT).asText(""),
                        source.path(METADATA_JSON).asText(null),
                        hit.path("_score").asDouble(0.0D)));
            }
            return results;
        } catch (IOException exception) {
            throw new ServiceException("解析 Elasticsearch 关键词检索结果失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new ServiceException("序列化 Elasticsearch 请求失败", exception,
                    BaseErrorCode.SERVICE_ERROR);
        }
    }

    private String truncateResponseBody(String body) {
        if (body == null || body.length() <= MAX_RESPONSE_BODY_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_RESPONSE_BODY_LENGTH);
    }

    private String resolveIndexName(String indexName) {
        if (StringUtils.hasText(indexName)) {
            return indexName;
        }
        return retrievalProperties.getKeyword().getIndexName();
    }

    private String baseUrl() {
        RetrievalProperties.Keyword keywordProperties = retrievalProperties.getKeyword();
        return keywordProperties.getScheme() + "://" + keywordProperties.getHost() + ":" + keywordProperties.getPort();
    }

    private String basicAuthorization(RetrievalProperties.Keyword keywordProperties) {
        String credential = keywordProperties.getUsername() + ":" + keywordProperties.getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
    }

    private String keywordIndexId(String indexName, String chunkId) {
        return "elasticsearch:" + indexName + ":" + chunkId;
    }

    private String encodePath(String path) {
        return URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
