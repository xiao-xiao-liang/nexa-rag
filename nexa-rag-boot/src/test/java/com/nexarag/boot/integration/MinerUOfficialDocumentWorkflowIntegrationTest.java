package com.nexarag.boot.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.boot.NexaRagApplication;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.enums.ChunkStatus;
import com.nexarag.document.enums.DocumentPipelineMessageStatus;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.infra.storage.service.FileStorageService;
import com.nexarag.infra.config.MinerUProperties;
import com.nexarag.retrieval.config.RetrievalProperties;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.GetReq;
import io.milvus.v2.service.vector.response.GetResp;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MinerU 官方解析完整入库链路测试，通过真实 HTTP 上传验证最终索引结果。
 */
@Slf4j
@Tag("integration")
@EnabledIfSystemProperty(named = "nexa.integration.workflow.enabled", matches = "true")
@ActiveProfiles("integration")
@SpringBootTest(classes = NexaRagApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.enabled=false")
class MinerUOfficialDocumentWorkflowIntegrationTest {

    private static final String EXAMPLE_PDF_URL =
            "https://cdn-mineru.openxlab.org.cn/demo/example.pdf";
    private static final Duration WORKFLOW_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration STATUS_POLL_INTERVAL = Duration.ofSeconds(3);

    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DocumentService documentService;
    private final DocumentChunkService documentChunkService;
    private final FileStorageService fileStorageService;
    private final RetrievalProperties retrievalProperties;
    private final MinerUProperties minerUProperties;

    @Autowired
    MinerUOfficialDocumentWorkflowIntegrationTest(TestRestTemplate restTemplate,
                                                  ObjectMapper objectMapper,
                                                  DocumentService documentService,
                                                  DocumentChunkService documentChunkService,
                                                  FileStorageService fileStorageService,
                                                  RetrievalProperties retrievalProperties,
                                                  MinerUProperties minerUProperties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.documentService = documentService;
        this.documentChunkService = documentChunkService;
        this.fileStorageService = fileStorageService;
        this.retrievalProperties = retrievalProperties;
        this.minerUProperties = minerUProperties;
    }

    @Test
    void uploadShouldCompleteOfficialMinerUWorkflowAndPersistIndexes() throws Exception {
        assumeTrue(StringUtils.hasText(minerUProperties.getApiKey()), "未配置 MinerU Token");
        String title = "MinerU官方完整链路测试-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        // 1. 优先继续验证已通过HTTP上传的文档，未指定时再创建新文档
        Long documentId = Long.getLong("nexa.integration.document-id");
        if (documentId == null) {
            byte[] pdfBytes = downloadExamplePdf();
            documentId = uploadDocument(title, pdfBytes);
        } else {
            Document existingDocument = documentService.getRequiredDocument(documentId);
            title = existingDocument.getTitle();
            if (existingDocument.getStatus() == DocumentStatus.FAILED) {
                retryDocument(documentId);
            }
        }
        log.info("完整入库测试已创建文档，documentId={}，title={}", documentId, title);

        // 2. 轮询真实异步流水线，直到完成或明确失败
        waitUntilIndexed(documentId);

        // 3. 核验数据库、MinIO、Milvus 和 Elasticsearch 入库结果
        verifyDatabaseAndStorage(documentId);
        DocumentChunk indexedChunk = requiredIndexedChunk(documentId);
        verifyMilvus(indexedChunk);
        verifyElasticsearch(documentId, indexedChunk);
        log.info("完整入库测试成功，documentId={}，title={}", documentId, title);
    }

    private Long uploadDocument(String title, byte[] pdfBytes) throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", buildFilePart(pdfBytes));
        body.add("request", buildRequestPart(title));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/documents/upload",
                new HttpEntity<>(body, headers),
                String.class
        );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode result = objectMapper.readTree(response.getBody());
        assertThat(result.path("code").asText()).isEqualTo("0");
        assertThat(result.at("/data/status").asText()).isEqualTo(DocumentStatus.QUEUED.name());
        long documentId = result.at("/data/documentId").asLong();
        assertThat(documentId).isPositive();
        return documentId;
    }

    private void retryDocument(Long documentId) throws Exception {
        // 1. 通过真实HTTP接口触发人工重试，验证失败文档重新入队能力
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/documents/{documentId}/retry",
                HttpEntity.EMPTY,
                String.class,
                documentId
        );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode result = objectMapper.readTree(response.getBody());
        assertThat(result.path("code").asText()).isEqualTo("0");
        assertThat(result.at("/data/status").asText()).isEqualTo(DocumentStatus.QUEUED.name());
        log.info("失败文档已通过HTTP接口重新入队，documentId={}", documentId);
    }

    private HttpEntity<ByteArrayResource> buildFilePart(byte[] pdfBytes) {
        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return "mineru-official-workflow.pdf";
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        return new HttpEntity<>(resource, headers);
    }

    private HttpEntity<String> buildRequestPart(String title) throws Exception {
        Map<String, Object> request = Map.of(
                "title", title,
                "description", "通过真实HTTP上传验证官方MinerU完整入库链路",
                "parseConfig", Map.of(
                        "enableOcr", false,
                        "enableImageDescription", false),
                "splitConfig", Map.of(
                        "splitStrategy", "BROTHER_MARKDOWN",
                        "chunkSize", 500,
                        "chunkOverlap", 50),
                "indexConfig", Map.of(
                        "enabled", true,
                        "vectorEnabled", true,
                        "keywordEnabled", true)
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(objectMapper.writeValueAsString(request), headers);
    }

    private void waitUntilIndexed(Long documentId) throws Exception {
        long deadline = System.nanoTime() + WORKFLOW_TIMEOUT.toNanos();
        JsonNode lastStatus = objectMapper.createObjectNode();
        while (System.nanoTime() < deadline) {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/api/documents/{documentId}/process-status",
                    String.class,
                    documentId
            );
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            lastStatus = objectMapper.readTree(response.getBody()).path("data");
            String status = lastStatus.path("status").asText();
            log.info("文档入库状态更新，documentId={}，status={}，messageStatus={}",
                    documentId, status, lastStatus.path("messageStatus").asText());
            if (DocumentStatus.INDEXED.name().equals(status)) {
                return;
            }
            if (DocumentStatus.FAILED.name().equals(status)) {
                fail("完整入库失败，documentId=%s，failureStage=%s，failureReason=%s"
                        .formatted(documentId,
                                lastStatus.path("failureStage").asText(),
                                lastStatus.path("failureReason").asText()));
            }
            Thread.sleep(STATUS_POLL_INTERVAL.toMillis());
        }
        fail("完整入库超时，documentId=%s，status=%s，messageStatus=%s"
                .formatted(documentId,
                        lastStatus.path("status").asText(),
                        lastStatus.path("messageStatus").asText()));
    }

    private void verifyDatabaseAndStorage(Long documentId) throws Exception {
        Document document = documentService.getRequiredDocument(documentId);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(document.getMessageStatus()).isEqualTo(DocumentPipelineMessageStatus.COMPLETED);
        assertThat(document.getOriginalObjectName()).isNotBlank();
        assertThat(document.getParsedObjectName()).isNotBlank();

        try (InputStream original = fileStorageService.load(document.getOriginalObjectName());
             InputStream parsed = fileStorageService.load(document.getParsedObjectName())) {
            assertThat(original.readAllBytes()).isNotEmpty();
            assertThat(new String(parsed.readAllBytes(), StandardCharsets.UTF_8)).isNotBlank();
        }

        List<DocumentChunk> chunks = documentChunkService.listByDocumentId(documentId);
        assertThat(chunks).isNotEmpty();
        List<DocumentChunk> indexRequiredChunks = chunks.stream()
                .filter(chunk -> !Integer.valueOf(1).equals(chunk.getSkipIndex()))
                .toList();
        assertThat(indexRequiredChunks).isNotEmpty();
        assertThat(indexRequiredChunks)
                .allMatch(chunk -> chunk.getStatus() == ChunkStatus.INDEXED)
                .allMatch(chunk -> StringUtils.hasText(chunk.getVectorId()))
                .allMatch(chunk -> StringUtils.hasText(chunk.getKeywordIndexId()));
    }

    private DocumentChunk requiredIndexedChunk(Long documentId) {
        return documentChunkService.listByDocumentId(documentId).stream()
                .filter(chunk -> chunk.getStatus() == ChunkStatus.INDEXED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到已索引文档分片，documentId=" + documentId));
    }

    private void verifyMilvus(DocumentChunk chunk) {
        RetrievalProperties.Vector vector = retrievalProperties.getVector();
        ConnectConfig.ConnectConfigBuilder connectBuilder = ConnectConfig.builder()
                .uri("http://" + vector.getHost() + ":" + vector.getPort())
                .rpcDeadlineMs(vector.getRpcDeadlineMs());
        if (StringUtils.hasText(vector.getDatabaseName())) {
            connectBuilder.dbName(vector.getDatabaseName());
        }
        if (StringUtils.hasText(vector.getUsername())) {
            connectBuilder.username(vector.getUsername());
        }
        if (StringUtils.hasText(vector.getPassword())) {
            connectBuilder.password(vector.getPassword());
        }
        MilvusClientV2 client = new MilvusClientV2(connectBuilder.build());
        try {
            GetResp response = client.get(GetReq.builder()
                    .collectionName(vector.getCollectionName())
                    .ids(List.of(chunk.getChunkId()))
                    .outputFields(List.of("chunk_id", "document_id"))
                    .build());
            assertThat(response.getGetResults()).isNotEmpty();
        } finally {
            client.close();
        }
    }

    private void verifyElasticsearch(Long documentId, DocumentChunk chunk) throws Exception {
        RetrievalProperties.Keyword keyword = retrievalProperties.getKeyword();
        sendElasticsearchRequest(keyword, "POST",
                "/" + encodePath(keyword.getIndexName()) + "/_refresh", null);
        String query = objectMapper.writeValueAsString(Map.of(
                "query", Map.of("term", Map.of(
                        "document_id", Map.of("value", documentId)))
        ));
        JsonNode result = sendElasticsearchRequest(keyword, "POST",
                "/" + encodePath(keyword.getIndexName()) + "/_search", query);
        assertThat(result.at("/hits/total/value").asInt()).isGreaterThan(0);
        assertThat(chunk.getText()).isNotBlank();
    }

    private JsonNode sendElasticsearchRequest(RetrievalProperties.Keyword keyword,
                                              String method,
                                              String path,
                                              String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(keyword.getScheme() + "://" + keyword.getHost() + ":" + keyword.getPort() + path))
                .timeout(Duration.ofMillis(keyword.getRequestTimeoutMs()))
                .method(method, publisher);
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (StringUtils.hasText(keyword.getUsername())) {
            String credential = keyword.getUsername() + ":" + keyword.getPassword();
            builder.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(credential.getBytes(StandardCharsets.UTF_8)));
        }
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isBetween(200, 299);
        if (!StringUtils.hasText(response.body())) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(response.body());
    }

    private byte[] downloadExamplePdf() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(EXAMPLE_PDF_URL))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.body()).isNotEmpty();
        return response.body();
    }

    private String encodePath(String path) {
        return URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
