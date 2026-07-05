package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexarag.document.dto.CreateDocumentRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.dto.SplitConfigRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.common.exception.ClientException;
import com.nexarag.document.vo.DocumentSummaryVO;
import com.nexarag.document.vo.PageVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档服务实现测试。
 */
class DocumentServiceImplTest {

    @Test
    void createDocumentShouldUseUploadedStatus() {
        TestableDocumentServiceImpl documentService = new TestableDocumentServiceImpl();

        Document document = documentService.createDocument(new CreateDocumentRequest(
                "测试文档", "描述", "demo.pdf", "original/demo.pdf", "minio://demo.pdf", 100L));

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(document.getOriginalFileName()).isEqualTo("demo.pdf");
        assertThat(document.getOriginalObjectName()).isEqualTo("original/demo.pdf");
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

    @Test
    void submitProcessShouldRejectWhenStatusChangedConcurrently() {
        TestableDocumentServiceImpl documentService = new TestableDocumentServiceImpl();
        documentService.updateResult = false;
        documentService.existingDocument = Document.builder()
                .documentId(1L)
                .status(DocumentStatus.UPLOADED)
                .retryCount(0)
                .maxRetryCount(3)
                .build();

        assertThatThrownBy(() -> documentService.submitProcess(1L, null))
                .isInstanceOf(ClientException.class);
    }

    @Test
    void recordProcessFailureShouldAutoRequeueBeforeRetryLimit() {
        TestableDocumentServiceImpl documentService = new TestableDocumentServiceImpl();
        documentService.existingDocument = Document.builder()
                .documentId(1L)
                .status(DocumentStatus.PARSING)
                .retryCount(0)
                .maxRetryCount(3)
                .build();

        Document document = documentService.recordProcessFailure(1L, "PARSE", "解析失败", "MinerU调用失败");

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(document.getQueueStage()).isEqualTo("PIPELINE");
        assertThat(document.getRetryCount()).isEqualTo(1);
        assertThat(document.getLastRetryTime()).isNotNull();
        assertThat(document.getFailureStage()).isEqualTo("PARSE");
        assertThat(document.getFailureReason()).isEqualTo("解析失败");
        assertThat(document.getFailureDetail()).isEqualTo("MinerU调用失败");
        assertThat(documentService.failureUpdatedDocument).isSameAs(document);
    }

    @Test
    void recordProcessFailureShouldMarkFailedWhenRetryLimitReached() {
        TestableDocumentServiceImpl documentService = new TestableDocumentServiceImpl();
        documentService.existingDocument = Document.builder()
                .documentId(1L)
                .status(DocumentStatus.PARSING)
                .retryCount(3)
                .maxRetryCount(3)
                .build();

        Document document = documentService.recordProcessFailure(1L, "PARSE", "解析失败", "MinerU调用失败");

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getRetryCount()).isEqualTo(3);
        assertThat(document.getProcessEndTime()).isNotNull();
        assertThat(document.getFailureStage()).isEqualTo("PARSE");
        assertThat(document.getFailureReason()).isEqualTo("解析失败");
        assertThat(document.getFailureDetail()).isEqualTo("MinerU调用失败");
        assertThat(documentService.failureUpdatedDocument).isSameAs(document);
    }

    @Test
    void retryProcessShouldRequeueFailedDocumentAfterAutoRetryLimit() {
        TestableDocumentServiceImpl documentService = new TestableDocumentServiceImpl();
        documentService.existingDocument = Document.builder()
                .documentId(1L)
                .status(DocumentStatus.FAILED)
                .retryCount(3)
                .maxRetryCount(3)
                .failureStage("PARSE")
                .failureReason("解析失败")
                .failureDetail("MinerU调用失败")
                .build();

        Document document = documentService.retryProcess(1L);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(document.getQueueStage()).isEqualTo("PIPELINE");
        assertThat(document.getRetryCount()).isZero();
        assertThat(document.getLastRetryTime()).isNotNull();
        assertThat(document.getProcessEndTime()).isNull();
        assertThat(document.getFailureStage()).isNull();
        assertThat(document.getFailureReason()).isNull();
        assertThat(document.getFailureDetail()).isNull();
        assertThat(documentService.retryUpdatedDocument).isSameAs(document);
    }

    @Test
    void deleteDocumentShouldUpdateDeleteTime() {
        TestableDocumentServiceImpl documentService = new TestableDocumentServiceImpl();
        documentService.existingDocument = Document.builder()
                .documentId(1L)
                .status(DocumentStatus.UPLOADED)
                .build();

        boolean deleted = documentService.deleteDocument(1L);

        assertThat(deleted).isTrue();
        assertThat(documentService.deleteDocumentId).isEqualTo(1L);
    }

    @Test
    void pageDocumentsShouldReturnSummaryPageVO() {
        TestableDocumentServiceImpl documentService = new TestableDocumentServiceImpl();
        Document document = Document.builder()
                .documentId(1L)
                .title("测试文档")
                .originalFileName("demo.pdf")
                .status(DocumentStatus.UPLOADED)
                .build();
        Page<Document> page = Page.of(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(document));
        documentService.documentPage = page;

        PageVO<DocumentSummaryVO> result = documentService.pageDocuments(1, 20);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.current()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().getFirst().documentId()).isEqualTo(1L);
    }

    private static class TestableDocumentServiceImpl extends DocumentServiceImpl {

        private Document existingDocument;
        private Document savedDocument;
        private Document updatedDocument;
        private Document failureUpdatedDocument;
        private Document retryUpdatedDocument;
        private Long deleteDocumentId;
        private IPage<Document> documentPage;
        private boolean updateResult = true;

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

        @Override
        protected boolean documentStatusUpdateFailed(Document document, DocumentStatus oldStatus) {
            this.updatedDocument = document;
            return !updateResult;
        }

        @Override
        protected boolean documentFailureUpdateFailed(Document document, DocumentStatus oldStatus) {
            this.failureUpdatedDocument = document;
            return !updateResult;
        }

        @Override
        protected boolean documentRetryUpdateFailed(Document document, DocumentStatus oldStatus) {
            this.retryUpdatedDocument = document;
            return !updateResult;
        }

        @Override
        protected boolean logicDeleteDocument(Long documentId) {
            this.deleteDocumentId = documentId;
            return true;
        }

        @Override
        protected IPage<Document> queryDocumentPage(long pageNum, long pageSize) {
            return documentPage;
        }
    }
}
