package com.nexarag.boot.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.boot.NexaRagApplication;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.enums.ChunkStatus;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.retrieval.dto.res.DocumentIndexResult;
import com.nexarag.retrieval.service.DocumentIndexService;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.GetReq;
import io.milvus.v2.service.vector.response.GetResp;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档真实检索入库集成测试，用于验证 ModelGateway 向量化后写入 Milvus 与 Elasticsearch 的完整链路。
 */
@Tag("integration")
@EnabledIfSystemProperty(named = "nexa.integration.enabled", matches = "true")
@SpringBootTest(classes = NexaRagApplication.class, properties = {
        "spring.flyway.enabled=false",
        "nexa.retrieval.embedding.type=model",
        "nexa.retrieval.vector.type=milvus",
        "nexa.retrieval.vector.host=192.168.0.134",
        "nexa.retrieval.vector.port=19530",
        "nexa.retrieval.vector.database-name=",
        "nexa.retrieval.vector.collection-name=nexa_document_chunk",
        "nexa.retrieval.keyword.type=elasticsearch",
        "nexa.retrieval.keyword.index-name=nexa_document_chunk",
        "spring.elasticsearch.uris=http://192.168.0.134:9200",
        "spring.elasticsearch.username=elastic",
        "spring.elasticsearch.password="
})
class DocumentMilvusIndexIntegrationTest {

    private static final Long DEFAULT_DOCUMENT_ID = 2026070504001L;
    private static final String COLLECTION_NAME = "nexa_document_chunk";
    private static final String KEYWORD_INDEX_NAME = "nexa_document_chunk";
    private static final String ES_BASE_URL = "http://192.168.0.134:9200";
    private static final String ES_USERNAME = "elastic";
    private static final String ES_PASSWORD = "";

    private final DocumentService documentService;
    private final DocumentVersionService documentVersionService;
    private final DocumentChunkService documentChunkService;
    private final DocumentIndexService documentIndexService;
    private final ObjectMapper objectMapper;

    @Autowired
    DocumentMilvusIndexIntegrationTest(DocumentService documentService,
                                       DocumentVersionService documentVersionService,
                                       DocumentChunkService documentChunkService,
                                       DocumentIndexService documentIndexService,
                                       ObjectMapper objectMapper) {
        this.documentService = documentService;
        this.documentVersionService = documentVersionService;
        this.documentChunkService = documentChunkService;
        this.documentIndexService = documentIndexService;
        this.objectMapper = objectMapper;
    }

    @Test
    void rebuildActiveDocumentVersionShouldEmbedByModelGatewayAndUpsertMilvusAndElasticsearch() {
        Long documentId = Long.getLong("nexa.integration.document-id", DEFAULT_DOCUMENT_ID);
        DocumentVersionDO activeVersion = getRequiredActiveVersion(documentId);
        Long documentVersionId = activeVersion.getDocumentVersionId();

        // 1. 对当前生效版本执行幂等索引回填，不改变版本状态或生效指针。
        DocumentIndexResult result = documentIndexService.rebuildDocumentVersionIndex(documentId, documentVersionId);

        // 2. 验证服务结果和当前版本片段的索引回写状态。
        assertThat(result.success()).isTrue();
        assertThat(result.vectorEnabled()).isTrue();
        assertThat(result.keywordEnabled()).isTrue();
        assertThat(result.indexedChunkCount()).isPositive();
        List<DocumentChunk> indexedChunks = documentChunkService.listByDocumentVersionId(documentVersionId).stream()
                .filter(chunk -> chunk.getStatus() == ChunkStatus.INDEXED)
                .toList();
        assertThat(indexedChunks).isNotEmpty();
        DocumentChunk firstIndexedChunk = indexedChunks.getFirst();
        assertThat(firstIndexedChunk.getVectorId()).startsWith("milvus:" + COLLECTION_NAME + ":");
        assertThat(firstIndexedChunk.getKeywordIndexId()).startsWith("elasticsearch:" + KEYWORD_INDEX_NAME + ":");

        // 3. 通过 Milvus 主键读取验证真实向量记录已经入库。
        assertMilvusContainsChunk(firstIndexedChunk.getChunkId());

        // 4. 通过 Elasticsearch 关键词查询验证指定版本的索引已经可检索。
        assertElasticsearchCanSearchChunk(documentId, documentVersionId, firstIndexedChunk);
    }

    private DocumentVersionDO getRequiredActiveVersion(Long documentId) {
        Document document = documentService.getRequiredDocument(documentId);
        DocumentVersionDO activeVersion = documentVersionService.getActiveVersionOrNull(document);
        assertThat(activeVersion).as("测试文档必须存在当前生效版本").isNotNull();
        return activeVersion;
    }

    private void assertMilvusContainsChunk(String chunkId) {
        MilvusClientV2 client = new MilvusClientV2(ConnectConfig.builder()
                .uri("http://192.168.0.134:19530")
                .rpcDeadlineMs(60000)
                .build());
        try {
            GetResp response = client.get(GetReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .ids(List.of(chunkId))
                    .outputFields(List.of("chunk_id", "document_id"))
                    .build());
            assertThat(response.getGetResults()).isNotEmpty();
        } finally {
            client.close();
        }
    }

    private void assertElasticsearchCanSearchChunk(Long documentId, Long documentVersionId, DocumentChunk chunk) {
        // 1. 刷新索引，保证刚写入的片段可以立即被检索
        sendElasticsearchRequest("POST", "/" + encodePath(KEYWORD_INDEX_NAME) + "/_refresh", null);

        // 2. 同时按文档和版本精确查询，确认当前版本片段已进入关键词索引。
        String termQuery = toJson(Map.of("query", Map.of("bool", Map.of("filter", List.of(
                Map.of("term", Map.of("document_id", Map.of("value", documentId))),
                Map.of("term", Map.of("document_version_id", Map.of("value", documentVersionId))))))));
        JsonNode termResult = sendElasticsearchRequest("POST", "/" + encodePath(KEYWORD_INDEX_NAME) + "/_search", termQuery);
        assertThat(termResult.at("/hits/total/value").asInt()).isGreaterThan(0);

        // 3. 按片段文本关键词查询，确认全文检索能命中刚写入的片段
        String keyword = resolveSearchKeyword(chunk.getText());
        String matchQuery = toJson(Map.of("query", Map.of("match", Map.of("text", keyword))));
        JsonNode matchResult = sendElasticsearchRequest("POST", "/" + encodePath(KEYWORD_INDEX_NAME) + "/_search", matchQuery);
        assertThat(matchResult.at("/hits/total/value").asInt()).isGreaterThan(0);
    }

    private JsonNode sendElasticsearchRequest(String method, String path, String body) {
        try {
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(ES_BASE_URL + path))
                    .method(method, publisher)
                    .header("Authorization", basicAuth());
            if (body != null) {
                builder.header("Content-Type", "application/json");
            }
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertThat(response.statusCode()).isBetween(200, 299);
            if (response.body() == null || response.body().isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("请求 Elasticsearch 失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求 Elasticsearch 被中断", exception);
        }
    }

    private String resolveSearchKeyword(String text) {
        if (text == null || text.isBlank()) {
            return "NexaRAG";
        }
        return text.length() > 40 ? text.substring(0, 40) : text;
    }

    private String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException exception) {
            throw new IllegalStateException("序列化 Elasticsearch 请求失败", exception);
        }
    }

    private String basicAuth() {
        String credential = ES_USERNAME + ":" + ES_PASSWORD;
        return "Basic " + Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
    }

    private String encodePath(String path) {
        return URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
