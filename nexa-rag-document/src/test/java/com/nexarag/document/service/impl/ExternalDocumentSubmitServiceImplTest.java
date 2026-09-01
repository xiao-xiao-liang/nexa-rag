package com.nexarag.document.service.impl;

import com.nexarag.document.model.dto.ExternalDocumentSubmitDTO;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.service.DocumentPipelineSubmitService;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.infra.source.ExternalDocumentSourceService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 外部文档统一受理服务测试。 */
class ExternalDocumentSubmitServiceImplTest {

    @Test
    void submitShouldValidateUrlThenCreateQueuedDocument() {
        ExternalDocumentSourceService sourceService = mock(ExternalDocumentSourceService.class);
        DocumentPipelineSubmitService pipelineService = mock(DocumentPipelineSubmitService.class);
        when(sourceService.validateAndExtractDocumentId(ExternalDocumentSourceType.YUQUE, "https://www.yuque.com/a/b"))
                .thenReturn("b");
        when(pipelineService.createAndSubmit(any(), any(), any(), any())).thenReturn(DocumentVersionDO.builder()
                .documentId(1L).documentVersionId(101L).processId("process-1")
                .status(DocumentVersionStatus.QUEUED).build());

        var result = new ExternalDocumentSubmitServiceImpl(sourceService, pipelineService, new com.nexarag.document.service.ProcessConfigDefaults())
                .submit(10L, new ExternalDocumentSubmitDTO(ExternalDocumentSourceType.YUQUE, "标题", null,
                        "https://www.yuque.com/a/b", null, null, null), "alice");

        assertThat(result.documentId()).isEqualTo(1L);
        verify(sourceService).validateAndExtractDocumentId(ExternalDocumentSourceType.YUQUE, "https://www.yuque.com/a/b");
        verify(pipelineService).createAndSubmit(any(), any(), any(), org.mockito.ArgumentMatchers.eq("alice"));
    }
}
