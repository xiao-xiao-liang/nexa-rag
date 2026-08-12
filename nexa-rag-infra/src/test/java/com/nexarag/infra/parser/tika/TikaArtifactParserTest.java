package com.nexarag.infra.parser.tika;

import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;
import com.nexarag.infra.constants.ParsedContentTypes;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.StoredFile;
import com.nexarag.infra.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tika 文档解析器测试。
 */
class TikaArtifactParserTest {

    @TempDir
    Path tempDir;

    @Test
    void supportedFormatsShouldContainPptAndTextOnly() {
        TikaArtifactParser parser = newParser("你好".getBytes(StandardCharsets.UTF_8));

        assertThat(parser.supportedFormats()).containsExactlyInAnyOrder(DocumentFormat.PPT, DocumentFormat.TEXT);
    }

    @Test
    void handleShouldUseDefaultFrameworkParserAndSaveParsedFile() throws Exception {
        RecordingFileStorageService storageService = new RecordingFileStorageService("你好，NexaRAG".getBytes(StandardCharsets.UTF_8));
        TikaArtifactParser parser = new TikaArtifactParser(storageService, new ObjectNameResolver());
        Path sourcePath = tempDir.resolve("source.txt");
        Files.writeString(sourcePath, "你好，NexaRAG");

        ParsedArtifact artifact = parser.handle(artifact(DocumentFormat.TEXT),
                new StagedDocumentBO(sourcePath, mock(com.nexarag.infra.parser.workspace.ArtifactWorkspace.class)));

        assertThat(artifact.contentType()).isEqualTo(ParsedContentTypes.TEXT_PLAIN);
        assertThat(artifact.objectKey()).isEqualTo("parsed/1/content.txt");
        assertThat(artifact.metadata()).containsEntry("parser", "tika");
        assertThat(storageService.savedContent).contains("你好，NexaRAG");
    }

    @Test
    void mergeDocumentTextsShouldKeepDocumentOrderWithBlankLineSeparator() {
        String content = TikaArtifactParser.mergeDocumentTexts(List.of(
                new org.springframework.ai.document.Document("第一页"),
                new org.springframework.ai.document.Document("第二页")));

        assertThat(content).isEqualTo("第一页\n\n第二页");
    }

    private TikaArtifactParser newParser(byte[] content) {
        return new TikaArtifactParser(new RecordingFileStorageService(content), new ObjectNameResolver());
    }

    private DocumentArtifactDTO artifact(DocumentFormat format) {
        return DocumentArtifactDTO.builder()
                .documentId(1L)
                .format(format)
                .originalFileName("demo.txt")
                .originalObjectName("original/demo.txt")
                .originalFileUrl("http://127.0.0.1:9000/nexa-rag/original/demo.txt")
                .build();
    }

    private static class RecordingFileStorageService implements FileStorageService {
        private final byte[] content;
        private String savedContent;

        private RecordingFileStorageService(byte[] content) {
            this.content = content;
        }

        @Override
        public StoredFile save(String fileName, InputStream inputStream, long size) {
            return null;
        }

        @Override
        public StoredFile saveAs(String objectName, InputStream inputStream, long size, String contentType) {
            try {
                this.savedContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            return new StoredFile(objectName, "http://127.0.0.1:9000/nexa-rag/" + objectName, size);
        }

        @Override
        public InputStream load(String objectName) {
            return new ByteArrayInputStream(content);
        }

        @Override
        public String resolveUrl(String objectName) {
            return "http://127.0.0.1:9000/nexa-rag/" + objectName;
        }

        @Override
        public void delete(String objectName) {
        }
    }
}
