package com.nexarag.document.service.impl;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.model.entity.DocumentSectionDO;
import com.nexarag.document.mapper.DocumentSectionMapper;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.model.bo.split.ChunkDraft;
import com.nexarag.document.model.bo.split.DocumentSplitResult;
import com.nexarag.document.model.bo.split.DocumentSectionDraft;
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
                "replaceDocumentVersionStructure", Long.class, Long.class, DocumentSplitResult.class);

        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    @Test
    void failureMethodShouldUseNewTransaction() throws NoSuchMethodException {
        Method method = DocumentProcessFailureService.class.getMethod(
                "recordFailure", Long.class, Long.class, String.class, String.class, String.class, String.class);

        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void persistenceShouldReplaceDocumentVersionStructureInReferentiallySafeOrder() {
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        DocumentSectionMapper sectionMapper = mock(DocumentSectionMapper.class);
        List<ChunkDraft> drafts = List.of(new ChunkDraft("chunk_1", null, 11L, "正文", "标题 > 正文", null, Map.of(), false));
        DocumentSplitResult splitResult = new DocumentSplitResult(List.of(
                new DocumentSectionDraft(11L, null, "标题", List.of("标题"), 1, 1, 2)), drafts, true);
        DocumentChunkPersistenceService service = new DocumentChunkPersistenceService(chunkService, sectionMapper);

        service.replaceDocumentVersionStructure(1L, 2L, splitResult);

        var ordered = inOrder(sectionMapper, chunkService);
        ordered.verify(chunkService).deleteByDocumentVersionId(2L);
        ordered.verify(sectionMapper).physicalDeleteByDocumentVersionId(2L);
        ordered.verify(sectionMapper).insert(org.mockito.ArgumentMatchers.any(DocumentSectionDO.class));
        ordered.verify(chunkService).saveDocumentVersionChunks(1L, 2L, drafts);
    }

    @Test
    void persistenceShouldRejectMissingDocumentVersionId() {
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        List<ChunkDraft> drafts = List.of(new ChunkDraft("chunk_1", null, "正文", null, Map.of(), false));
        DocumentChunkPersistenceService service = new DocumentChunkPersistenceService(chunkService,
                mock(DocumentSectionMapper.class));

        assertThatThrownBy(() -> service.replaceDocumentVersionStructure(1L, null,
                DocumentSplitResult.unstructured(drafts)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文档版本切分结果不能为空");
    }

    @Test
    void persistenceShouldNotSaveChunksWhenSectionPersistenceFails() {
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        DocumentSectionMapper sectionMapper = mock(DocumentSectionMapper.class);
        DocumentSplitResult splitResult = new DocumentSplitResult(List.of(
                new DocumentSectionDraft(11L, null, "标题", List.of("标题"), 1, 1, 2)),
                List.of(new ChunkDraft("chunk_1", null, 11L, "正文", "标题 > 正文", null, Map.of(), false)), true);
        when(sectionMapper.insert(org.mockito.ArgumentMatchers.any(DocumentSectionDO.class)))
                .thenThrow(new IllegalStateException("章节保存失败"));
        DocumentChunkPersistenceService service = new DocumentChunkPersistenceService(chunkService, sectionMapper);

        assertThatThrownBy(() -> service.replaceDocumentVersionStructure(1L, 2L, splitResult))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("章节保存失败");

        verify(chunkService, never()).saveDocumentVersionChunks(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void persistenceShouldNotMarkDocumentChunkedWhenChunkPersistenceFails() {
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        DocumentSectionMapper sectionMapper = mock(DocumentSectionMapper.class);
        List<ChunkDraft> drafts = List.of(new ChunkDraft("chunk_1", null, "正文", null, Map.of(), false));
        doThrow(new IllegalStateException("片段保存失败")).when(chunkService)
                .saveDocumentVersionChunks(1L, 2L, drafts);
        DocumentChunkPersistenceService service = new DocumentChunkPersistenceService(chunkService, sectionMapper);

        assertThatThrownBy(() -> service.replaceDocumentVersionStructure(1L, 2L,
                DocumentSplitResult.unstructured(drafts)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("片段保存失败");

        verify(sectionMapper, never()).insert(org.mockito.ArgumentMatchers.any(DocumentSectionDO.class));
    }

    @Test
    void failureServiceShouldDelegateFailureRecording() {
        com.nexarag.document.service.DocumentVersionService documentVersionService =
                mock(com.nexarag.document.service.DocumentVersionService.class);
        when(documentVersionService.recordRetryableFailure(1L, 2L, "process-1", "CHUNK", "文档切分失败", "测试异常"))
                .thenReturn(true);
        DocumentProcessFailureService service = new DocumentProcessFailureService(
                documentVersionService, org.mockito.Mockito.mock(com.nexarag.document.service.DocumentTaskAlertService.class),
                org.mockito.Mockito.mock(com.nexarag.document.service.DocumentPipelineOutboxService.class));

        boolean result = service.recordFailure(1L, 2L, "process-1", "CHUNK", "文档切分失败", "测试异常");

        assertThat(result).isTrue();
        verify(documentVersionService).recordRetryableFailure(1L, 2L, "process-1", "CHUNK", "文档切分失败", "测试异常");
    }
}
