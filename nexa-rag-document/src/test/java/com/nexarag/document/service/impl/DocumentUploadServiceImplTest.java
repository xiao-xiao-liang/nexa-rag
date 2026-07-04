package com.nexarag.document.service.impl;

import com.nexarag.document.dto.CreateDocumentRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.dto.UploadDocumentRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.service.DocumentProcessTaskDispatcher;
import com.nexarag.document.service.DocumentQueueInfo;
import com.nexarag.document.service.ProcessConfigDefaults;
import com.nexarag.document.vo.UploadDocumentResponse;
import com.nexarag.infra.storage.service.FileStorageService;
import com.nexarag.infra.storage.StoredFile;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档上传服务实现测试。
 */
class DocumentUploadServiceImplTest {

    @Test
    void uploadShouldStoreFileCreateDocumentSubmitProcessAndReturnQueueInfo() {
        RecordingFileStorageService fileStorageService = new RecordingFileStorageService();
        RecordingDocumentService documentService = new RecordingDocumentService();
        FixedDocumentProcessTaskDispatcher dispatcher = new FixedDocumentProcessTaskDispatcher();
        DocumentUploadServiceImpl uploadService = new DocumentUploadServiceImpl(
                fileStorageService, documentService, new ProcessConfigDefaults(), dispatcher);
        MockMultipartFile file = new MockMultipartFile("file", "demo.pdf", "application/pdf", "hello".getBytes());

        UploadDocumentResponse response = uploadService.upload(file,
                new UploadDocumentRequest(null, "描述", null, null, null));

        assertThat(fileStorageService.savedFileName).isEqualTo("demo.pdf");
        assertThat(fileStorageService.savedSize).isEqualTo(5L);
        assertThat(documentService.createdRequest.title()).isEqualTo("demo.pdf");
        assertThat(documentService.createdRequest.description()).isEqualTo("描述");
        assertThat(documentService.createdRequest.originalFileUrl())
                .isEqualTo("http://127.0.0.1:9000/nexa-rag/original/demo.pdf");
        assertThat(documentService.submittedRequest.splitConfig()).isNotNull();
        assertThat(dispatcher.enqueuedDocumentId).isEqualTo(1L);
        assertThat(response.documentId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(response.queuePosition()).isEqualTo(3);
        assertThat(response.waitingCount()).isEqualTo(5);
    }

    private static class RecordingFileStorageService implements FileStorageService {

        private String savedFileName;
        private long savedSize;

        @Override
        public StoredFile save(String fileName, InputStream inputStream, long size) {
            this.savedFileName = fileName;
            this.savedSize = size;
            return new StoredFile("original/demo.pdf", "http://127.0.0.1:9000/nexa-rag/original/demo.pdf", size);
        }

        @Override
        public InputStream load(String objectName) {
            return InputStream.nullInputStream();
        }

        @Override
        public void delete(String objectName) {
        }
    }

    private static class RecordingDocumentService extends DocumentServiceImpl {

        private CreateDocumentRequest createdRequest;
        private ProcessDocumentRequest submittedRequest;
        private Document document;

        @Override
        public Document createDocument(CreateDocumentRequest request) {
            this.createdRequest = request;
            this.document = Document.builder()
                    .documentId(1L)
                    .title(request.title())
                    .description(request.description())
                    .originalFileName(request.originalFileName())
                    .originalFileUrl(request.originalFileUrl())
                    .fileSize(request.fileSize())
                    .fileType(FileType.fromFileName(request.originalFileName()))
                    .status(DocumentStatus.UPLOADED)
                    .build();
            return document;
        }

        @Override
        public Document submitProcess(Long documentId, ProcessDocumentRequest request) {
            this.submittedRequest = request;
            document.setStatus(DocumentStatus.QUEUED);
            return document;
        }
    }

    private static class FixedDocumentProcessTaskDispatcher implements DocumentProcessTaskDispatcher {

        private Long enqueuedDocumentId;

        @Override
        public DocumentQueueInfo enqueue(Long documentId) {
            this.enqueuedDocumentId = documentId;
            return new DocumentQueueInfo(3, 5);
        }
    }
}
