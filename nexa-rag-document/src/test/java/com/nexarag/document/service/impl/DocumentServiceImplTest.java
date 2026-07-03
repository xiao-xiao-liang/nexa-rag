package com.nexarag.document.service.impl;

import com.nexarag.document.dto.CreateDocumentRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.dto.SplitConfigRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.SplitStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档服务实现测试。
 */
class DocumentServiceImplTest {

    @Test
    void createDocumentShouldUseUploadedStatus() {
        TestableDocumentServiceImpl documentService = new TestableDocumentServiceImpl();

        Document document = documentService.createDocument(new CreateDocumentRequest(
                "测试文档", "描述", "demo.pdf", "minio://demo.pdf", 100L));

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(document.getOriginalFileName()).isEqualTo("demo.pdf");
        assertThat(documentService.savedDocument).isSameAs(document);
    }

    @Test
    void submitProcessShouldTransferToQueued() {
        TestableDocumentServiceImpl documentService = new TestableDocumentServiceImpl();
        documentService.existingDocument = Document.builder()
                .documentId(1L)
                .status(DocumentStatus.UPLOADED)
                .retryCount(0)
                .maxRetryCount(3)
                .build();

        Document document = documentService.submitProcess(1L,
                new ProcessDocumentRequest(new SplitConfigRequest(SplitStrategy.PARENT_MARKDOWN, 1000, 100)));

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(document.getQueueStage()).isEqualTo("PIPELINE");
        assertThat(document.getProcessConfigJson()).contains("PARENT_MARKDOWN");
        assertThat(documentService.updatedDocument).isSameAs(document);
    }

    private static class TestableDocumentServiceImpl extends DocumentServiceImpl {

        private Document existingDocument;
        private Document savedDocument;
        private Document updatedDocument;

        @Override
        public boolean save(Document entity) {
            this.savedDocument = entity;
            return true;
        }

        @Override
        public Document getById(java.io.Serializable id) {
            return existingDocument;
        }

        @Override
        public boolean updateById(Document entity) {
            this.updatedDocument = entity;
            return true;
        }
    }
}
