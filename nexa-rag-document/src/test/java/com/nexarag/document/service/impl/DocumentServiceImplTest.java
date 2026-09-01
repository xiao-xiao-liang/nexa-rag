package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.model.dto.CreateDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.model.vo.DocumentProcessStatusVO;
import com.nexarag.document.model.vo.DocumentSummaryVO;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentDeleteTaskService;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.document.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档稳定身份服务测试。
 */
class DocumentServiceImplTest {

    @Test
    void createDocumentShouldOnlyPersistStableIdentityFields() {
        TestableDocumentServiceImpl service = new TestableDocumentServiceImpl();

        Document document = service.createDocument(new CreateDocumentRequest(
                "测试文档", "描述", "demo.pdf", "original/demo.pdf", "minio://demo.pdf", 100L));

        assertThat(document.getDocumentId()).isNotNull();
        assertThat(document.getTitle()).isEqualTo("测试文档");
        assertThat(document.getActiveVersionId()).isNull();
        assertThat(document.getBuildingVersionId()).isNull();
        assertThat(document.getActivationGeneration()).isZero();
        assertThat(service.savedDocument).isSameAs(document);
    }

    @Test
    void pageDocumentsShouldProjectActiveVersion() {
        TestableDocumentServiceImpl service = new TestableDocumentServiceImpl();
        Document document = Document.builder().documentId(1L).title("测试文档").activeVersionId(11L).build();
        Page<Document> page = Page.of(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(document));
        service.documentPage = page;
        when(service.documentVersionService.findActiveVersions(List.of(document)))
                .thenReturn(Map.of(1L, activeVersion(1L, 11L, "v1.md")));

        var result = service.pageDocuments(1, 20);

        DocumentSummaryVO summary = result.getRecords().getFirst();
        assertThat(summary.originalFileName()).isEqualTo("v1.md");
        assertThat(summary.fileSize()).isEqualTo(2048L);
        assertThat(summary.status()).isEqualTo(DocumentStatus.INDEXED);
    }

    @Test
    void processStatusShouldProjectActiveVersionInsteadOfBuildingVersion() {
        TestableDocumentServiceImpl service = new TestableDocumentServiceImpl();
        service.existingDocument = Document.builder().documentId(1L).title("测试文档")
                .activeVersionId(11L).buildingVersionId(12L).build();
        when(service.documentVersionService.getActiveVersionOrNull(service.existingDocument))
                .thenReturn(activeVersion(1L, 11L, "v1.md").toBuilder().processId("v1-process").build());

        DocumentProcessStatusVO status = service.getProcessStatus(1L);

        assertThat(status.processId()).isEqualTo("v1-process");
        assertThat(status.status()).isEqualTo(DocumentStatus.INDEXED);
    }

    @Test
    void deleteDocumentShouldCreateOneIndexCleanupTaskPerVersion() {
        TestableDocumentServiceImpl service = new TestableDocumentServiceImpl();
        service.existingDocument = Document.builder().documentId(1L).build();
        when(service.documentVersionService.markAllVersionsDeleting(1L, "alice"))
                .thenReturn(List.of(activeVersion(1L, 11L, "v1.md"), activeVersion(1L, 12L, "v2.md")));
        when(service.deleteTaskService.createVersionIndexCleanupTask(1L, 11L)).thenReturn(99L);
        when(service.deleteTaskService.createVersionIndexCleanupTask(1L, 12L)).thenReturn(100L);

        var deleted = service.deleteDocument(1L, "alice");

        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.cleanupOutboxId()).isEqualTo(99L);
        verify(service.deleteTaskService).createVersionIndexCleanupTask(1L, 11L);
        verify(service.deleteTaskService).createVersionIndexCleanupTask(1L, 12L);
        verify(service.documentVersionService).markAllVersionsDeleting(1L, "alice");
    }

    private static DocumentVersionDO activeVersion(Long documentId, Long documentVersionId, String fileName) {
        return DocumentVersionDO.builder()
                .documentId(documentId)
                .documentVersionId(documentVersionId)
                .originalFileName(fileName)
                .fileType(FileType.MARKDOWN)
                .fileSize(2048L)
                .status(DocumentVersionStatus.INDEX_READY)
                .build();
    }

    private static class TestableDocumentServiceImpl extends DocumentServiceImpl {

        private Document existingDocument;
        private Document savedDocument;
        private IPage<Document> documentPage;
        private final DocumentDeleteTaskService deleteTaskService;
        private final DocumentVersionService documentVersionService;

        private TestableDocumentServiceImpl() {
            this(mock(DocumentChunkService.class), mock(DocumentDeleteTaskService.class),
                    mock(KnowledgeBaseService.class), mock(DocumentVersionService.class));
        }

        private TestableDocumentServiceImpl(DocumentChunkService documentChunkService,
                                            DocumentDeleteTaskService deleteTaskService,
                                            KnowledgeBaseService knowledgeBaseService,
                                            DocumentVersionService documentVersionService) {
            super(documentChunkService, deleteTaskService, knowledgeBaseService, documentVersionService);
            this.deleteTaskService = deleteTaskService;
            this.documentVersionService = documentVersionService;
        }

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
        protected boolean logicDeleteDocument(Long documentId) {
            return true;
        }

        @Override
        protected IPage<Document> queryDocumentPage(long pageNum, long pageSize) {
            return documentPage;
        }
    }
}
