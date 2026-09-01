package com.nexarag.document.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.model.dto.CreateDocumentRequest;
import com.nexarag.common.exception.ClientException;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.model.entity.DocumentTaskOutboxDO;
import com.nexarag.document.enums.DocumentTaskStatus;
import com.nexarag.document.enums.DocumentTaskType;
import com.nexarag.document.enums.OutboxPublishStatus;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.infra.config.DocumentPipelineMessagingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档流水线提交服务测试，验证处理批次和Outbox消息会同步创建。
 */
class DocumentPipelineSubmitServiceImplTest {

    @Test
    void submitProcessShouldRejectLegacyDocumentLevelProcessing() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentPipelineMessagingProperties properties = new DocumentPipelineMessagingProperties();
        DocumentPipelineSubmitServiceImpl submitService = new DocumentPipelineSubmitServiceImpl(
                documentService, mock(DocumentVersionService.class), outboxService, properties,
                new ObjectMapper().findAndRegisterModules());
        ProcessDocumentRequest request = new ProcessDocumentRequest(null, null, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> submitService.submitProcess(1L, request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("版本接口");

        verify(outboxService, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retryProcessShouldRejectLegacyDocumentLevelProcessing() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentPipelineSubmitServiceImpl submitService = new DocumentPipelineSubmitServiceImpl(
                documentService, mock(DocumentVersionService.class), outboxService, new DocumentPipelineMessagingProperties(),
                new ObjectMapper().findAndRegisterModules());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> submitService.retryProcess(1L))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("版本接口");

        verify(outboxService, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createAndSubmitShouldCreateDocumentBeforeSubmitting() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentPipelineSubmitServiceImpl submitService = new DocumentPipelineSubmitServiceImpl(
                documentService, documentVersionService, outboxService, new DocumentPipelineMessagingProperties(),
                new ObjectMapper().findAndRegisterModules());
        when(outboxService.save(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        CreateDocumentRequest createRequest = new CreateDocumentRequest(
                "测试", null, "demo.pdf", "original/demo.pdf", "http://demo", 1L);
        ProcessDocumentRequest processRequest = new ProcessDocumentRequest(null, null, null);
        when(documentService.createDocument(10L, createRequest)).thenReturn(com.nexarag.document.model.entity.Document.builder()
                .documentId(1L).build());
        when(documentVersionService.createNextVersion(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(), anyString(), org.mockito.ArgumentMatchers.eq("alice")))
                .thenReturn(DocumentVersionDO.builder().documentId(1L).documentVersionId(101L)
                        .status(DocumentVersionStatus.UPLOADED).build());
        when(documentVersionService.updateById(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        DocumentVersionDO result = submitService.createAndSubmit(10L, createRequest, processRequest, "alice");

        assertThat(result.getProcessId()).isNotBlank();
        assertThat(result.getStatus()).isEqualTo(DocumentVersionStatus.QUEUED);
        assertThat(result.getDocumentVersionId()).isEqualTo(101L);
        verify(documentService).createDocument(10L, createRequest);
        ArgumentCaptor<com.nexarag.document.model.dto.DocumentVersionUploadDTO> uploadCaptor =
                ArgumentCaptor.forClass(com.nexarag.document.model.dto.DocumentVersionUploadDTO.class);
        verify(documentVersionService).createNextVersion(org.mockito.ArgumentMatchers.eq(1L), uploadCaptor.capture(),
                anyString(), org.mockito.ArgumentMatchers.eq("alice"));
        assertThat(uploadCaptor.getValue().originalFileName()).isEqualTo("demo.pdf");
        ArgumentCaptor<DocumentVersionDO> versionCaptor = ArgumentCaptor.forClass(DocumentVersionDO.class);
        verify(documentVersionService).updateById(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getDocumentVersionId()).isEqualTo(101L);
        assertThat(versionCaptor.getValue().getStatus()).isEqualTo(DocumentVersionStatus.QUEUED);
        assertThat(versionCaptor.getValue().getProcessId()).isEqualTo(result.getProcessId());
        assertThat(versionCaptor.getValue().getUpdateBy()).isEqualTo("alice");
        ArgumentCaptor<DocumentTaskOutboxDO> outboxCaptor = ArgumentCaptor.forClass(DocumentTaskOutboxDO.class);
        verify(outboxService).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getDocumentVersionId()).isEqualTo(101L);
        assertThat(outboxCaptor.getValue().getMessageBody()).contains("\"documentVersionId\":101");
    }

}
