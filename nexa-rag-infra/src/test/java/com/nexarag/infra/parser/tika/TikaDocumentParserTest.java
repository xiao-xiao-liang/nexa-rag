package com.nexarag.infra.parser.tika;

import com.nexarag.infra.parser.DocumentParseRequest;
import com.nexarag.infra.parser.DocumentParseResult;
import com.nexarag.infra.parser.ParsedContentTypes;
import com.nexarag.infra.parser.ParserFileTypes;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.StoredFile;
import com.nexarag.infra.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tika 文档解析器测试。
 */
class TikaDocumentParserTest {

    @Test
    void supportsShouldAcceptPptAndTextOnly() {
        TikaDocumentParser parser = newParser("你好".getBytes(StandardCharsets.UTF_8));

        assertThat(parser.supports(request(ParserFileTypes.PPT))).isTrue();
        assertThat(parser.supports(request(ParserFileTypes.TEXT))).isTrue();
        assertThat(parser.supports(request(ParserFileTypes.EXCEL))).isFalse();
    }

    @Test
    void parseShouldExtractTextAndSaveParsedFile() {
        RecordingFileStorageService storageService = new RecordingFileStorageService("你好，NexaRAG".getBytes(StandardCharsets.UTF_8));
        TikaDocumentParser parser = new TikaDocumentParser(storageService, new ObjectNameResolver());

        DocumentParseResult result = parser.parse(request(ParserFileTypes.TEXT));

        assertThat(result.contentType()).isEqualTo(ParsedContentTypes.TEXT_PLAIN);
        assertThat(result.content()).contains("你好");
        assertThat(result.parsedObjectName()).isEqualTo("parsed/1/content.txt");
        assertThat(storageService.savedContent).contains("你好");
    }

    private TikaDocumentParser newParser(byte[] content) {
        return new TikaDocumentParser(new RecordingFileStorageService(content), new ObjectNameResolver());
    }

    private DocumentParseRequest request(String fileType) {
        return DocumentParseRequest.builder()
                .documentId(1L)
                .fileType(fileType)
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
        public void delete(String objectName) {
        }
    }
}
