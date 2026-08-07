package com.nexarag.document.service.impl;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentSectionDO;
import com.nexarag.document.mapper.DocumentSectionMapper;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.splitter.ChunkDraft;
import com.nexarag.document.splitter.DocumentSplitResult;
import com.nexarag.document.splitter.DocumentSectionDraft;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档切分事务服务测试。
 */
class DocumentChunkTransactionServiceTest {

    @Test
    void persistenceMethodShouldUseRequiredTransaction() throws NoSuchMethodException {
        Method method = DocumentChunkPersistenceService.class.getMethod(
                "replaceDocumentStructure", Long.class, DocumentSplitResult.class);

        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transactional.rollbackFor()).contains(Exception.class);
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
    void persistenceShouldReplaceDocumentStructureInReferentiallySafeOrder() {
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        DocumentService documentService = mock(DocumentService.class);
        DocumentSectionMapper sectionMapper = mock(DocumentSectionMapper.class);
        List<ChunkDraft> drafts = List.of(new ChunkDraft("chunk_1", null, 11L, "正文", "标题 > 正文", null, Map.of(), false));
        DocumentSplitResult splitResult = new DocumentSplitResult(List.of(
                new DocumentSectionDraft(11L, null, "标题", List.of("标题"), 1, 1, 2)), drafts, true);
        when(documentService.markChunked(1L)).thenReturn(true);
        DocumentChunkPersistenceService service = new DocumentChunkPersistenceService(chunkService, documentService, sectionMapper);

        service.replaceDocumentStructure(1L, splitResult);

        var ordered = inOrder(sectionMapper, chunkService, documentService);
        ordered.verify(chunkService).deleteByDocumentId(1L);
        ordered.verify(sectionMapper).physicalDeleteByDocumentId(1L);
        ordered.verify(sectionMapper).insert(org.mockito.ArgumentMatchers.any(DocumentSectionDO.class));
        ordered.verify(chunkService).saveDocumentChunks(1L, drafts);
        ordered.verify(documentService).markChunked(1L);
    }

    @Test
    void persistenceShouldThrowWhenDocumentStatusChanged() {
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        DocumentService documentService = mock(DocumentService.class);
        List<ChunkDraft> drafts = List.of(new ChunkDraft("chunk_1", null, "正文", null, Map.of(), false));
        when(documentService.markChunked(1L)).thenReturn(false);
        DocumentChunkPersistenceService service = new DocumentChunkPersistenceService(chunkService, documentService,
                mock(DocumentSectionMapper.class));

        assertThatThrownBy(() -> service.replaceDocumentStructure(1L, DocumentSplitResult.unstructured(drafts)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("更新文档切分完成状态失败");
    }

    @Test
    void persistenceShouldNotSaveChunksWhenSectionPersistenceFails() {
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        DocumentService documentService = mock(DocumentService.class);
        DocumentSectionMapper sectionMapper = mock(DocumentSectionMapper.class);
        DocumentSplitResult splitResult = new DocumentSplitResult(List.of(
                new DocumentSectionDraft(11L, null, "标题", List.of("标题"), 1, 1, 2)),
                List.of(new ChunkDraft("chunk_1", null, 11L, "正文", "标题 > 正文", null, Map.of(), false)), true);
        when(sectionMapper.insert(org.mockito.ArgumentMatchers.any(DocumentSectionDO.class)))
                .thenThrow(new IllegalStateException("章节保存失败"));
        DocumentChunkPersistenceService service = new DocumentChunkPersistenceService(chunkService, documentService, sectionMapper);

        assertThatThrownBy(() -> service.replaceDocumentStructure(1L, splitResult))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("章节保存失败");

        verify(chunkService, never()).saveDocumentChunks(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(documentService, never()).markChunked(1L);
    }

    @Test
    void persistenceShouldNotMarkDocumentChunkedWhenChunkPersistenceFails() {
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        DocumentService documentService = mock(DocumentService.class);
        DocumentSectionMapper sectionMapper = mock(DocumentSectionMapper.class);
        List<ChunkDraft> drafts = List.of(new ChunkDraft("chunk_1", null, "正文", null, Map.of(), false));
        doThrow(new IllegalStateException("片段保存失败")).when(chunkService).saveDocumentChunks(1L, drafts);
        DocumentChunkPersistenceService service = new DocumentChunkPersistenceService(chunkService, documentService, sectionMapper);

        assertThatThrownBy(() -> service.replaceDocumentStructure(1L, DocumentSplitResult.unstructured(drafts)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("片段保存失败");

        verify(documentService, never()).markChunked(1L);
        verify(sectionMapper, never()).insert(org.mockito.ArgumentMatchers.any(DocumentSectionDO.class));
    }

    @Test
    void failureServiceShouldDelegateFailureRecording() {
        DocumentService documentService = mock(DocumentService.class);
        Document document = Document.builder().documentId(1L).build();
        when(documentService.recordProcessFailure(1L, "CHUNK", "文档切分失败", "测试异常"))
                .thenReturn(document);
        DocumentProcessFailureService service = new DocumentProcessFailureService(
                documentService, org.mockito.Mockito.mock(com.nexarag.document.service.DocumentTaskAlertService.class));

        Document result = service.recordFailure(1L, "CHUNK", "文档切分失败", "测试异常");

        assertThat(result).isSameAs(document);
        verify(documentService).recordProcessFailure(1L, "CHUNK", "文档切分失败", "测试异常");
    }
}
