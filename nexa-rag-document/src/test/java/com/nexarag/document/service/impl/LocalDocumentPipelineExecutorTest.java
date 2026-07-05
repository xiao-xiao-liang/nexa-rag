package com.nexarag.document.service.impl;

import com.nexarag.document.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.service.DocumentChunkingService;
import com.nexarag.infra.parser.DocumentParseRequest;
import com.nexarag.infra.parser.DocumentParseResult;
import com.nexarag.infra.parser.DocumentParseService;
import com.nexarag.infra.parser.ParsedContentTypes;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地文档流水线执行器测试。
 */
class LocalDocumentPipelineExecutorTest {

    @Test
    void executeShouldParseQueuedDocumentAndMarkParsed() {
        TestableDocumentService documentService = new TestableDocumentService();
        documentService.existingDocument = Document.builder()
                .documentId(1L)
                .originalFileName("demo.pdf")
                .originalObjectName("original/demo.pdf")
                .originalFileUrl("http://127.0.0.1:9000/nexa-rag/original/demo.pdf")
                .fileType(FileType.PDF)
                .status(DocumentStatus.QUEUED)
                .processConfigJson("{\"parseConfig\":{\"enableOcr\":true,\"enableImageDescription\":false}}")
                .build();
        RecordingDocumentParseService parseService = new RecordingDocumentParseService();
        RecordingDocumentChunkingService chunkingService = new RecordingDocumentChunkingService();
        LocalDocumentPipelineExecutor executor = new LocalDocumentPipelineExecutor(documentService, parseService,
                chunkingService);

        executor.execute(1L);

        assertThat(parseService.request.enableOcr()).isTrue();
        assertThat(parseService.request.fileType()).isEqualTo("PDF");
        assertThat(documentService.savedStatuses).containsExactly(DocumentStatus.PARSING, DocumentStatus.PARSED);
        assertThat(documentService.existingDocument.getParsedObjectName()).isEqualTo("parsed/1/content.md");
        assertThat(documentService.existingDocument.getParsedContentType()).isEqualTo(ParsedContentTypes.TEXT_MARKDOWN);
        assertThat(documentService.existingDocument.getParsedFileUrl()).isEqualTo("http://127.0.0.1:9000/nexa-rag/parsed/1/content.md");
        assertThat(chunkingService.documentId).isEqualTo(1L);
    }

    @Test
    void executeShouldRecordFailureAndThrowWhenDocumentNeedsRetry() {
        TestableDocumentService documentService = new TestableDocumentService();
        documentService.existingDocument = Document.builder()
                .documentId(1L)
                .originalFileName("demo.pdf")
                .originalObjectName("original/demo.pdf")
                .fileType(FileType.PDF)
                .status(DocumentStatus.QUEUED)
                .retryCount(0)
                .maxRetryCount(3)
                .build();
        FailingDocumentParseService parseService = new FailingDocumentParseService();
        RecordingDocumentChunkingService chunkingService = new RecordingDocumentChunkingService();
        LocalDocumentPipelineExecutor executor = new LocalDocumentPipelineExecutor(documentService, parseService,
                chunkingService);

        assertThatThrownBy(() -> executor.execute(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("文档解析失败");

        assertThat(documentService.failureStage).isEqualTo("PARSE");
        assertThat(documentService.savedStatuses).containsExactly(DocumentStatus.PARSING);
        assertThat(chunkingService.documentId).isNull();
    }

    private static class RecordingDocumentParseService implements DocumentParseService {

        private DocumentParseRequest request;

        @Override
        public DocumentParseResult parse(DocumentParseRequest request) {
            this.request = request;
            return DocumentParseResult.builder()
                    .contentType(ParsedContentTypes.TEXT_MARKDOWN)
                    .content("# title")
                    .parsedObjectName("parsed/1/content.md")
                    .parsedFileUrl("http://127.0.0.1:9000/nexa-rag/parsed/1/content.md")
                    .metadata(Map.of("parser", "test"))
                    .build();
        }
    }

    private static class FailingDocumentParseService implements DocumentParseService {

        @Override
        public DocumentParseResult parse(DocumentParseRequest request) {
            throw new IllegalStateException("MinerU调用失败");
        }
    }

    private static class RecordingDocumentChunkingService implements DocumentChunkingService {

        private Long documentId;

        @Override
        public int chunk(Long documentId) {
            this.documentId = documentId;
            return 1;
        }
    }

    private static class TestableDocumentService extends DocumentServiceImpl {

        private Document existingDocument;
        private final List<DocumentStatus> savedStatuses = new ArrayList<>();
        private String failureStage;

        @Override
        public Document getById(Serializable id) {
            return existingDocument;
        }

        @Override
        public boolean updateById(Document entity) {
            savedStatuses.add(entity.getStatus());
            return true;
        }

        @Override
        public Document recordProcessFailure(Long documentId,
                                             String failureStage,
                                             String failureReason,
                                             String failureDetail) {
            this.failureStage = failureStage;
            existingDocument.setStatus(DocumentStatus.QUEUED);
            return existingDocument;
        }
    }
}
