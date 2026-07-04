package com.nexarag.document.service.impl;

import com.nexarag.document.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.vo.DocumentProcessStatusVO;
import com.nexarag.infra.queue.document.DocumentPipelineQueue;
import com.nexarag.infra.queue.document.DocumentPipelineQueueStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 文档队列状态服务测试。
 */
class DocumentQueueStatusServiceImplTest {

    @Test
    void getProcessStatusShouldReturnWaitingQueueFields() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentPipelineQueue queue = mock(DocumentPipelineQueue.class);
        when(documentService.getRequiredDocument(1L)).thenReturn(buildQueuedDocument());
        when(queue.queryStatus(1L)).thenReturn(Optional.of(
                new DocumentPipelineQueueStatus(1L, 3, 8, false, null, null, 101L)));
        DocumentQueueStatusServiceImpl service = new DocumentQueueStatusServiceImpl(documentService, queue);

        DocumentProcessStatusVO vo = service.getProcessStatus(1L);

        assertThat(vo.status()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(vo.queuePosition()).isEqualTo(3);
        assertThat(vo.waitingCount()).isEqualTo(8);
        assertThat(vo.running()).isFalse();
    }

    @Test
    void getProcessStatusShouldReturnRunningQueueFields() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentPipelineQueue queue = mock(DocumentPipelineQueue.class);
        when(documentService.getRequiredDocument(1L)).thenReturn(buildQueuedDocument());
        when(queue.queryStatus(1L)).thenReturn(Optional.of(
                new DocumentPipelineQueueStatus(1L, null, 2, true, "worker-1", 120L, 102L)));
        DocumentQueueStatusServiceImpl service = new DocumentQueueStatusServiceImpl(documentService, queue);

        DocumentProcessStatusVO vo = service.getProcessStatus(1L);

        assertThat(vo.queuePosition()).isNull();
        assertThat(vo.waitingCount()).isEqualTo(2);
        assertThat(vo.running()).isTrue();
        assertThat(vo.workerId()).isEqualTo("worker-1");
        assertThat(vo.leaseTtlSeconds()).isEqualTo(120L);
    }

    @Test
    void getProcessStatusShouldReturnStableStatusWhenRedisStatusMissing() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentPipelineQueue queue = mock(DocumentPipelineQueue.class);
        when(documentService.getRequiredDocument(1L)).thenReturn(buildQueuedDocument());
        when(queue.queryStatus(1L)).thenReturn(Optional.empty());
        DocumentQueueStatusServiceImpl service = new DocumentQueueStatusServiceImpl(documentService, queue);

        DocumentProcessStatusVO vo = service.getProcessStatus(1L);

        assertThat(vo.status()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(vo.queuePosition()).isNull();
        assertThat(vo.waitingCount()).isNull();
        assertThat(vo.running()).isFalse();
    }

    private Document buildQueuedDocument() {
        return Document.builder()
                .documentId(1L)
                .status(DocumentStatus.QUEUED)
                .retryCount(0)
                .build();
    }
}