package com.nexarag.document.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.dto.CreateDocumentRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.enums.DocumentPipelineMessageStatus;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.outbox.entity.DocumentPipelineOutbox;
import com.nexarag.document.outbox.enums.OutboxPublishStatus;
import com.nexarag.document.outbox.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.vo.DocumentProcessStatusVO;
import com.nexarag.infra.messaging.document.config.DocumentPipelineMessagingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档流水线提交服务测试，验证处理批次和Outbox消息会同步创建。
 */
class DocumentPipelineSubmitServiceImplTest {

    @Test
    void submitProcessShouldGenerateProcessIdAndSavePendingOutbox() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentPipelineMessagingProperties properties = new DocumentPipelineMessagingProperties();
        DocumentPipelineSubmitServiceImpl submitService = new DocumentPipelineSubmitServiceImpl(
                documentService, outboxService, properties, new ObjectMapper().findAndRegisterModules());
        when(outboxService.save(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        ProcessDocumentRequest request = new ProcessDocumentRequest(null, null, null);
        when(documentService.submitProcess(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(request), anyString()))
                .thenAnswer(invocation -> queuedDocument(invocation.getArgument(2)));

        DocumentProcessStatusVO result = submitService.submitProcess(1L, request);

        ArgumentCaptor<DocumentPipelineOutbox> captor = ArgumentCaptor.forClass(DocumentPipelineOutbox.class);
        verify(outboxService).save(captor.capture());
        DocumentPipelineOutbox outbox = captor.getValue();
        assertThat(result.status()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(outbox.getDocumentId()).isEqualTo(1L);
        assertThat(outbox.getProcessId()).isNotBlank();
        assertThat(outbox.getMessageKey()).isEqualTo("1:" + outbox.getProcessId());
        assertThat(outbox.getPublishStatus()).isEqualTo(OutboxPublishStatus.PENDING);
        assertThat(outbox.getTopic()).isEqualTo(properties.getTopic());
        assertThat(outbox.getMessageBody()).contains(outbox.getProcessId());
    }

    @Test
    void retryProcessShouldGenerateNewProcessIdAndSaveNewOutbox() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentPipelineSubmitServiceImpl submitService = new DocumentPipelineSubmitServiceImpl(
                documentService, outboxService, new DocumentPipelineMessagingProperties(),
                new ObjectMapper().findAndRegisterModules());
        when(outboxService.save(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(documentService.retryProcess(org.mockito.ArgumentMatchers.eq(1L), anyString()))
                .thenAnswer(invocation -> queuedDocument(invocation.getArgument(1)));

        submitService.retryProcess(1L);
        submitService.retryProcess(1L);

        ArgumentCaptor<String> processIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(documentService, org.mockito.Mockito.times(2)).retryProcess(
                org.mockito.ArgumentMatchers.eq(1L), processIdCaptor.capture());
        assertThat(processIdCaptor.getAllValues()).hasSize(2).doesNotHaveDuplicates();
        verify(outboxService, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createAndSubmitShouldCreateDocumentBeforeSubmitting() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentPipelineSubmitServiceImpl submitService = new DocumentPipelineSubmitServiceImpl(
                documentService, outboxService, new DocumentPipelineMessagingProperties(),
                new ObjectMapper().findAndRegisterModules());
        when(outboxService.save(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        CreateDocumentRequest createRequest = new CreateDocumentRequest(
                "测试", null, "demo.pdf", "original/demo.pdf", "http://demo", 1L);
        ProcessDocumentRequest processRequest = new ProcessDocumentRequest(null, null, null);
        when(documentService.createDocument(createRequest)).thenReturn(Document.builder().documentId(1L).build());
        when(documentService.submitProcess(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(processRequest), anyString()))
                .thenAnswer(invocation -> queuedDocument(invocation.getArgument(2)));

        Document result = submitService.createAndSubmit(createRequest, processRequest);

        assertThat(result.getProcessId()).isNotBlank();
        verify(documentService).createDocument(createRequest);
        verify(outboxService).save(org.mockito.ArgumentMatchers.any());
    }

    private Document queuedDocument(String processId) {
        return Document.builder()
                .documentId(1L)
                .processId(processId)
                .status(DocumentStatus.QUEUED)
                .messageStatus(DocumentPipelineMessageStatus.PENDING_PUBLISH)
                .retryCount(0)
                .build();
    }
}
