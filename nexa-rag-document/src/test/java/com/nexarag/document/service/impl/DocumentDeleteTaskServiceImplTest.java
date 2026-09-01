package com.nexarag.document.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.enums.DocumentTaskStatus;
import com.nexarag.document.enums.DocumentTaskType;
import com.nexarag.document.enums.OutboxPublishStatus;
import com.nexarag.document.model.entity.DocumentTaskOutboxDO;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.infra.config.DocumentTaskMessagingProperties;
import com.nexarag.infra.messaging.document.task.DocumentVersionIndexCleanupMessage;
import com.nexarag.infra.messaging.document.task.DocumentVersionStorageCleanupMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档删除任务创建服务测试。
 */
class DocumentDeleteTaskServiceImplTest {

    @Test
    void shouldPersistVersionCleanupTasksWithDocumentVersionBoundary() throws Exception {
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        when(outboxService.save(org.mockito.ArgumentMatchers.any(DocumentTaskOutboxDO.class))).thenReturn(true);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DocumentDeleteTaskServiceImpl service = new DocumentDeleteTaskServiceImpl(outboxService,
                new DocumentTaskMessagingProperties(), objectMapper);

        service.createVersionIndexCleanupTask(1L, 2L);
        service.createVersionStorageCleanupTask(DocumentVersionDO.builder().documentId(1L).documentVersionId(2L)
                .originalObjectName("original/v2.pdf").parsedObjectName("parsed/v2.md").build());

        ArgumentCaptor<DocumentTaskOutboxDO> captor = ArgumentCaptor.forClass(DocumentTaskOutboxDO.class);
        verify(outboxService, org.mockito.Mockito.times(2)).save(captor.capture());
        DocumentTaskOutboxDO indexTask = captor.getAllValues().getFirst();
        DocumentTaskOutboxDO storageTask = captor.getAllValues().get(1);
        assertThat(indexTask.getDocumentVersionId()).isEqualTo(2L);
        assertThat(indexTask.getTaskType()).isEqualTo(DocumentTaskType.CLEAN_DOCUMENT_VERSION_INDEX);
        assertThat(indexTask.getMessageKey()).contains("1:2:CLEAN_DOCUMENT_VERSION_INDEX:");
        assertThat(objectMapper.readValue(indexTask.getMessageBody(), DocumentVersionIndexCleanupMessage.class)
                .documentVersionId()).isEqualTo(2L);
        assertThat(storageTask.getDocumentVersionId()).isEqualTo(2L);
        assertThat(storageTask.getTaskType()).isEqualTo(DocumentTaskType.CLEAN_DOCUMENT_VERSION_STORAGE);
        assertThat(objectMapper.readValue(storageTask.getMessageBody(), DocumentVersionStorageCleanupMessage.class)
                .originalObjectName()).isEqualTo("original/v2.pdf");
    }
}
