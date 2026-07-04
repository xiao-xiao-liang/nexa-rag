package com.nexarag.infra.parser.mineru;

import com.nexarag.infra.config.StorageProperties;
import com.nexarag.infra.parser.DocumentParseRequest;
import com.nexarag.infra.parser.DocumentParseResult;
import com.nexarag.infra.parser.ParsedContentTypes;
import com.nexarag.infra.parser.ParserFileTypes;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.StoredFile;
import com.nexarag.infra.storage.minio.MinioFileStorageStrategy;
import com.nexarag.infra.storage.service.FileStorageService;
import com.nexarag.infra.storage.service.FileStorageServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MinerU 真实解析集成测试，显式开启后连接本地 MinIO 和 MinerU。
 */
class MinerUDocumentParserIntegrationTest {

    /**
     * 使用真实 PDF 验证 MinIO 原文入库、MinerU 解析、Markdown 产物入库。
     *
     * @throws Exception 文件读写或远程服务调用失败时抛出
     */
    @Test
    @EnabledIfSystemProperty(named = "nexa.parser.integration.enabled", matches = "true")
    void parseShouldUseRealMinioAndMinerU() throws Exception {
        // 1. 准备真实外部服务配置和测试文件
        Path pdfPath = Path.of(System.getProperty("nexa.parser.integration.file", "D:\\下载\\Reactive Stream.pdf"));
        assertThat(Files.exists(pdfPath)).as("测试 PDF 文件必须存在").isTrue();
        long documentId = Long.parseLong(System.getProperty("nexa.parser.integration.document-id",
                String.valueOf(System.currentTimeMillis())));
        FileStorageService fileStorageService = buildFileStorageService();

        // 2. 将原始 PDF 保存到 MinIO，模拟上传阶段已完成
        StoredFile originalFile;
        try (InputStream inputStream = Files.newInputStream(pdfPath)) {
            originalFile = fileStorageService.save(pdfPath.getFileName().toString(), inputStream, Files.size(pdfPath));
        }

        // 3. 调用真实 MinerU 完成 PDF 到 Markdown 的解析
        MinerUDocumentParser parser = new MinerUDocumentParser(
                fileStorageService,
                new ObjectNameResolver(),
                new LocalMinerUClient(buildMinerUProperties()),
                new MinerUZipResultExtractor(),
                new MarkdownImageUrlRewriter()
        );
        DocumentParseResult result = parser.parse(DocumentParseRequest.builder()
                .documentId(documentId)
                .originalFileName(pdfPath.getFileName().toString())
                .fileType(ParserFileTypes.PDF)
                .originalObjectName(originalFile.objectName())
                .originalFileUrl(originalFile.url())
                .enableOcr(true)
                .enableImageDescription(false)
                .build());

        // 4. 从 MinIO 读回 Markdown 产物并校验核心内容
        String savedMarkdown;
        try (InputStream inputStream = fileStorageService.load(result.parsedObjectName())) {
            savedMarkdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(result.contentType()).isEqualTo(ParsedContentTypes.TEXT_MARKDOWN);
        assertThat(result.parsedObjectName()).isEqualTo("parsed/" + documentId + "/content.md");
        assertThat(result.content()).isEqualTo(savedMarkdown);
        assertThat(savedMarkdown).containsIgnoringCase("Reactive");
        assertThat(savedMarkdown).containsIgnoringCase("Stream");
        assertThat(savedMarkdown.length()).isGreaterThan(1000);
    }

    private FileStorageService buildFileStorageService() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setEndpoint(System.getProperty("nexa.storage.endpoint", "http://127.0.0.1:9000"));
        storageProperties.setAccessKey(System.getProperty("nexa.storage.access-key", "liang"));
        storageProperties.setSecretKey(System.getProperty("nexa.storage.secret-key", "liang123"));
        storageProperties.setBucket(System.getProperty("nexa.storage.bucket", "nexa-rag"));
        storageProperties.setCreateBucket(Boolean.parseBoolean(System.getProperty("nexa.storage.create-bucket", "true")));
        MinioFileStorageStrategy strategy = new MinioFileStorageStrategy(storageProperties, new ObjectNameResolver());
        return new FileStorageServiceImpl(storageProperties, List.of(strategy));
    }

    private MinerUProperties buildMinerUProperties() {
        MinerUProperties properties = new MinerUProperties();
        properties.setLocalEndpoint(System.getProperty("nexa.parser.mineru.local-endpoint", "http://127.0.0.1:8000"));
        properties.setLocalParsePath(System.getProperty("nexa.parser.mineru.local-parse-path", "/file_parse"));
        return properties;
    }
}
