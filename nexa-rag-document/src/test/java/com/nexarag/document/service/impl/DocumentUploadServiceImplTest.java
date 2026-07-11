package com.nexarag.document.service.impl;

import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
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
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                fileStorageService, documentService, new ProcessConfigDefaults(), dispatcher,
                multipartProperties());
        MockMultipartFile file = new MockMultipartFile("file", "demo.pdf", "application/pdf", "hello".getBytes());

        UploadDocumentResponse response = uploadService.upload(file,
                new UploadDocumentRequest(null, "描述", null, null, null));

        assertThat(fileStorageService.savedFileName).isEqualTo("demo.pdf");
        assertThat(fileStorageService.savedSize).isEqualTo(5L);
        assertThat(documentService.createdRequest.title()).isEqualTo("demo.pdf");
        assertThat(documentService.createdRequest.description()).isEqualTo("描述");
        assertThat(documentService.createdRequest.originalFileUrl())
                .isEqualTo("http://127.0.0.1:9000/nexa-rag/original/demo.pdf");
        assertThat(documentService.createdRequest.originalObjectName()).isEqualTo("original/demo.pdf");
        assertThat(documentService.submittedRequest.splitConfig()).isNotNull();
        assertThat(dispatcher.enqueuedDocumentId).isEqualTo(1L);
        assertThat(response.documentId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(response.queuePosition()).isEqualTo(3);
        assertThat(response.waitingCount()).isEqualTo(5);
    }

    @Test
    void uploadShouldRejectFileLargerThanMultipartLimitBeforeStorage() {
        RecordingFileStorageService storageService = new RecordingFileStorageService();
        MultipartProperties properties = multipartProperties();
        properties.setMaxFileSize(DataSize.ofBytes(4));
        DocumentUploadServiceImpl uploadService = new DocumentUploadServiceImpl(
                storageService, new RecordingDocumentService(), new ProcessConfigDefaults(),
                new FixedDocumentProcessTaskDispatcher(), properties);
        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> uploadService.upload(file, null))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("文件大小超过限制");
        assertThat(storageService.savedFileName).isNull();
    }

    @Test
    void uploadShouldRejectUnsupportedFileTypeBeforeStorage() {
        RecordingFileStorageService storageService = new RecordingFileStorageService();
        DocumentUploadServiceImpl uploadService = new DocumentUploadServiceImpl(
                storageService, new RecordingDocumentService(), new ProcessConfigDefaults(),
                new FixedDocumentProcessTaskDispatcher(), multipartProperties());
        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.exe", "application/octet-stream", new byte[]{1});

        assertThatThrownBy(() -> uploadService.upload(file, null))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("不支持的文档类型");
        assertThat(storageService.savedFileName).isNull();
    }

    @Test
    void uploadShouldDeleteStoredObjectWhenCreateDocumentFails() {
        RecordingFileStorageService storageService = new RecordingFileStorageService();
        RecordingDocumentService documentService = new RecordingDocumentService();
        documentService.createException = new ServiceException("模拟文档创建失败");
        DocumentUploadServiceImpl uploadService = new DocumentUploadServiceImpl(
                storageService, documentService, new ProcessConfigDefaults(),
                new FixedDocumentProcessTaskDispatcher(), multipartProperties());
        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> uploadService.upload(file, null))
                .isSameAs(documentService.createException);
        assertThat(storageService.deletedObjectName).isEqualTo("original/demo.pdf");
    }

    @Test
    void uploadShouldKeepStoredObjectWhenEnqueueFailsAfterDocumentCreated() {
        RecordingFileStorageService storageService = new RecordingFileStorageService();
        ServiceException enqueueException = new ServiceException("模拟Redis入队失败");
        DocumentProcessTaskDispatcher dispatcher = documentId -> {
            throw enqueueException;
        };
        DocumentUploadServiceImpl uploadService = new DocumentUploadServiceImpl(
                storageService, new RecordingDocumentService(), new ProcessConfigDefaults(),
                dispatcher, multipartProperties());
        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> uploadService.upload(file, null))
                .isSameAs(enqueueException);
        assertThat(storageService.deletedObjectName).isNull();
    }

    @Test
    void uploadShouldKeepOriginalExceptionWhenStoredObjectCleanupFails() {
        RecordingFileStorageService storageService = new RecordingFileStorageService();
        storageService.deleteException = new IllegalStateException("模拟对象删除失败");
        RecordingDocumentService documentService = new RecordingDocumentService();
        ServiceException createException = new ServiceException("模拟文档创建失败");
        documentService.createException = createException;
        DocumentUploadServiceImpl uploadService = new DocumentUploadServiceImpl(
                storageService, documentService, new ProcessConfigDefaults(),
                new FixedDocumentProcessTaskDispatcher(), multipartProperties());
        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> uploadService.upload(file, null))
                .isSameAs(createException)
                .satisfies(exception -> assertThat(exception.getSuppressed())
                        .extracting(Throwable::getMessage)
                        .containsExactly("模拟对象删除失败"));
    }

    private MultipartProperties multipartProperties() {
        MultipartProperties properties = new MultipartProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(100));
        properties.setMaxRequestSize(DataSize.ofMegabytes(110));
        return properties;
    }

    private static class RecordingFileStorageService implements FileStorageService {

        private String savedFileName;
        private long savedSize;
        private String deletedObjectName;
        private RuntimeException deleteException;

        @Override
        public StoredFile save(String fileName, InputStream inputStream, long size) {
            this.savedFileName = fileName;
            this.savedSize = size;
            return new StoredFile("original/demo.pdf", "http://127.0.0.1:9000/nexa-rag/original/demo.pdf", size);
        }

        @Override
        public StoredFile saveAs(String objectName, InputStream inputStream, long size, String contentType) {
            return new StoredFile(objectName, "http://127.0.0.1:9000/nexa-rag/" + objectName, size);
        }

        @Override
        public InputStream load(String objectName) {
            return InputStream.nullInputStream();
        }

        @Override
        public void delete(String objectName) {
            this.deletedObjectName = objectName;
            if (deleteException != null) {
                throw deleteException;
            }
        }
    }

    private static class RecordingDocumentService extends DocumentServiceImpl {

        private CreateDocumentRequest createdRequest;
        private ProcessDocumentRequest submittedRequest;
        private Document document;
        private RuntimeException createException;

        @Override
        public Document createDocument(CreateDocumentRequest request) {
            if (createException != null) {
                throw createException;
            }
            this.createdRequest = request;
            this.document = Document.builder()
                    .documentId(1L)
                    .title(request.title())
                    .description(request.description())
                    .originalFileName(request.originalFileName())
                    .originalFileUrl(request.originalFileUrl())
                    .originalObjectName(request.originalObjectName())
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
