package com.nexarag.document.service.impl;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.entity.Document;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.splitter.ChunkDraft;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档切分事务服务测试。
 */
class DocumentChunkTransactionServiceTest {

    @Test
    void persistenceMethodShouldUseRequiredTransaction() throws NoSuchMethodException {
        Method method = DocumentChunkPersistenceService.class.getMethod(
                "replaceChunksAndMarkChunked", Long.class, java.util.List.class);

        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }

    @Test
    void failureMethodShouldUseNewTransaction() throws NoSuchMethodException {
        Method method = DocumentProcessFailureService.class.getMethod(
                "recordFailure", Long.class, String.class, String.class, String.class);

        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void persistenceShouldReplaceChunksBeforeMarkingDocumentChunked() {
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        DocumentService documentService = mock(DocumentService.class);
        List<ChunkDraft> drafts = List.of(new ChunkDraft("chunk_1", null, "正文", null, Map.of(), false));
        when(documentService.markChunked(1L)).thenReturn(true);
        DocumentChunkPersistenceService service = new DocumentChunkPersistenceService(chunkService, documentService);

        service.replaceChunksAndMarkChunked(1L, drafts);

        var ordered = inOrder(chunkService, documentService);
        ordered.verify(chunkService).replaceDocumentChunks(1L, drafts);
        ordered.verify(documentService).markChunked(1L);
    }

    @Test
    void persistenceShouldThrowWhenDocumentStatusChanged() {
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        DocumentService documentService = mock(DocumentService.class);
        List<ChunkDraft> drafts = List.of(new ChunkDraft("chunk_1", null, "正文", null, Map.of(), false));
        when(documentService.markChunked(1L)).thenReturn(false);
        DocumentChunkPersistenceService service = new DocumentChunkPersistenceService(chunkService, documentService);

        assertThatThrownBy(() -> service.replaceChunksAndMarkChunked(1L, drafts))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("更新文档切分完成状态失败");
    }

    @Test
    void failureServiceShouldDelegateFailureRecording() {
        DocumentService documentService = mock(DocumentService.class);
        Document document = Document.builder().documentId(1L).build();
        when(documentService.recordProcessFailure(1L, "CHUNK", "文档切分失败", "测试异常"))
                .thenReturn(document);
        DocumentProcessFailureService service = new DocumentProcessFailureService(
                documentService, org.mockito.Mockito.mock(com.nexarag.document.alert.DocumentPipelineAlertService.class));

        Document result = service.recordFailure(1L, "CHUNK", "文档切分失败", "测试异常");

        assertThat(result).isSameAs(document);
        verify(documentService).recordProcessFailure(1L, "CHUNK", "文档切分失败", "测试异常");
    }
}
