package com.nexarag.document.service.impl;

import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.service.DocumentProcessTaskDispatcher;
import com.nexarag.document.service.DocumentQueueInfo;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.vo.DocumentProcessStatusVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档流水线触发服务测试，验证手工提交和重试会真实投递处理队列。
 */
class DocumentPipelineTriggerServiceImplTest {

    @Test
    void submitProcessShouldSubmitDocumentAndEnqueuePipelineTask() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentProcessTaskDispatcher dispatcher = mock(DocumentProcessTaskDispatcher.class);
        DocumentPipelineTriggerServiceImpl triggerService = new DocumentPipelineTriggerServiceImpl(
                documentService, dispatcher);
        ProcessDocumentRequest request = new ProcessDocumentRequest(null, null, null);
        Document document = Document.builder()
                .documentId(1L)
                .status(DocumentStatus.QUEUED)
                .retryCount(0)
                .build();
        when(documentService.submitProcess(1L, request)).thenReturn(document);
        when(dispatcher.enqueue(1L)).thenReturn(new DocumentQueueInfo(2, 4));

        DocumentProcessStatusVO result = triggerService.submitProcess(1L, request);

        assertThat(result.documentId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(result.queuePosition()).isEqualTo(2);
        assertThat(result.waitingCount()).isEqualTo(4);
        verify(documentService).submitProcess(1L, request);
        verify(dispatcher).enqueue(1L);
    }

    @Test
    void retryProcessShouldRetryDocumentAndEnqueuePipelineTask() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentProcessTaskDispatcher dispatcher = mock(DocumentProcessTaskDispatcher.class);
        DocumentPipelineTriggerServiceImpl triggerService = new DocumentPipelineTriggerServiceImpl(
                documentService, dispatcher);
        Document document = Document.builder()
                .documentId(1L)
                .status(DocumentStatus.QUEUED)
                .retryCount(0)
                .build();
        when(documentService.retryProcess(1L)).thenReturn(document);
        when(dispatcher.enqueue(1L)).thenReturn(new DocumentQueueInfo(1, 1));

        DocumentProcessStatusVO result = triggerService.retryProcess(1L);

        assertThat(result.documentId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(result.queuePosition()).isEqualTo(1);
        assertThat(result.waitingCount()).isEqualTo(1);
        verify(documentService).retryProcess(1L);
        verify(dispatcher).enqueue(1L);
    }
}
