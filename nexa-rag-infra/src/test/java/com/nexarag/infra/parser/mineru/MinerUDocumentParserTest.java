package com.nexarag.infra.parser.mineru;

import com.nexarag.infra.parser.DocumentParseRequest;
import com.nexarag.infra.parser.DocumentParseResult;
import com.nexarag.infra.parser.ParsedContentTypes;
import com.nexarag.infra.parser.ParserFileTypes;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.StoredFile;
import com.nexarag.infra.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MinerU 文档解析器测试。
 */
class MinerUDocumentParserTest {

    @Test
    void supportsShouldAcceptPdfAndWordOnly() {
        MinerUDocumentParser parser = newParser(zip(Map.of("content.md", "# title")));

        assertThat(parser.supports(request(ParserFileTypes.PDF))).isTrue();
        assertThat(parser.supports(request(ParserFileTypes.WORD))).isTrue();
        assertThat(parser.supports(request(ParserFileTypes.PPT))).isFalse();
    }

    @Test
    void parseShouldSaveAssetsRewriteMarkdownAndSaveParsedMarkdown() throws Exception {
        RecordingFileStorageService storageService = new RecordingFileStorageService("original".getBytes(StandardCharsets.UTF_8));
        MinerUDocumentParser parser = new MinerUDocumentParser(
                storageService,
                new ObjectNameResolver(),
                new StubMinerUClient(zip(Map.of(
                        "result/content.md", "# title\n![img](images/a.png)",
                        "result/images/a.png", "fake-image"
                ))),
                new MinerUZipResultExtractor(),
                new MarkdownImageUrlRewriter()
        );

        DocumentParseResult result = parser.parse(request(ParserFileTypes.PDF));

        assertThat(result.contentType()).isEqualTo(ParsedContentTypes.TEXT_MARKDOWN);
        assertThat(result.parsedObjectName()).isEqualTo("parsed/1/content.md");
        assertThat(storageService.savedObjects).containsKey("parsed/1/content.md");
        assertThat(storageService.savedObjects).anySatisfy((objectName, content) -> {
            assertThat(objectName).startsWith("parsed/1/assets/");
            assertThat(content).isEqualTo("fake-image");
        });
        assertThat(storageService.savedObjects.get("parsed/1/content.md"))
                .contains("# title")
                .contains("http://127.0.0.1:9000/nexa-rag/parsed/1/assets/");
        assertThat(result.content()).contains("http://127.0.0.1:9000/nexa-rag/parsed/1/assets/");
    }

    private MinerUDocumentParser newParser(byte[] zipBytes) {
        return new MinerUDocumentParser(
                new RecordingFileStorageService("original".getBytes(StandardCharsets.UTF_8)),
                new ObjectNameResolver(),
                new StubMinerUClient(zipBytes),
                new MinerUZipResultExtractor(),
                new MarkdownImageUrlRewriter()
        );
    }

    private DocumentParseRequest request(String fileType) {
        return DocumentParseRequest.builder()
                .documentId(1L)
                .originalFileName("demo.pdf")
                .fileType(fileType)
                .originalObjectName("original/demo.pdf")
                .originalFileUrl("http://127.0.0.1:9000/nexa-rag/original/demo.pdf")
                .enableOcr(true)
                .enableImageDescription(false)
                .build();
    }

    private byte[] zip(Map<String, String> entries) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                    zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zipOutputStream.closeEntry();
                }
            }
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static class StubMinerUClient implements MinerUClient {

        private final byte[] zipBytes;

        private StubMinerUClient(byte[] zipBytes) {
            this.zipBytes = zipBytes;
        }

        @Override
        public MinerUParseResponse parse(MinerUParseCommand command) {
            return MinerUParseResponse.builder()
                    .zipInputStream(new ByteArrayInputStream(zipBytes))
                    .metadata(Map.of("client", "stub"))
                    .build();
        }
    }

    private static class RecordingFileStorageService implements FileStorageService {

        private final byte[] originalContent;
        private final Map<String, String> savedObjects = new LinkedHashMap<>();

        private RecordingFileStorageService(byte[] originalContent) {
            this.originalContent = originalContent;
        }

        @Override
        public StoredFile save(String fileName, InputStream inputStream, long size) {
            return null;
        }

        @Override
        public StoredFile saveAs(String objectName, InputStream inputStream, long size, String contentType) {
            try {
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                savedObjects.put(objectName, content);
                return new StoredFile(objectName, "http://127.0.0.1:9000/nexa-rag/" + objectName, size);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public InputStream load(String objectName) {
            return new ByteArrayInputStream(originalContent);
        }

        @Override
        public void delete(String objectName) {
        }
    }
}
