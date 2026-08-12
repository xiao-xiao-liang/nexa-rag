package com.nexarag.document.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.enums.DocumentTaskStatus;
import com.nexarag.document.enums.DocumentTaskType;
import com.nexarag.document.enums.OutboxPublishStatus;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentTaskOutboxDO;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.infra.config.DocumentTaskMessagingProperties;
import com.nexarag.infra.messaging.document.task.DocumentStorageCleanupMessage;
import com.nexarag.infra.storage.ObjectNameResolver;
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
    void shouldPersistStorageCleanupTaskWithDocumentObjectNames() throws Exception {
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        when(outboxService.save(org.mockito.ArgumentMatchers.any(DocumentTaskOutboxDO.class))).thenReturn(true);
        DocumentDeleteTaskServiceImpl service = new DocumentDeleteTaskServiceImpl(outboxService,
                new DocumentTaskMessagingProperties(), new ObjectMapper().findAndRegisterModules(),
                new ObjectNameResolver());
        Document document = Document.builder()
                .documentId(1L)
                .originalObjectName("original/demo.pdf")
                .parsedObjectName("parsed/demo.md")
                .build();

        service.createStorageCleanupTask(document);

        ArgumentCaptor<DocumentTaskOutboxDO> captor = ArgumentCaptor.forClass(DocumentTaskOutboxDO.class);
        verify(outboxService).save(captor.capture());
        DocumentTaskOutboxDO outbox = captor.getValue();
        assertThat(outbox.getDocumentId()).isEqualTo(1L);
        assertThat(outbox.getTaskType()).isEqualTo(DocumentTaskType.CLEAN_DOCUMENT_STORAGE);
        assertThat(outbox.getTopic()).isEqualTo("nexa-document-storage-cleanup");
        assertThat(outbox.getPublishStatus()).isEqualTo(OutboxPublishStatus.PENDING);
        assertThat(outbox.getTaskStatus()).isEqualTo(DocumentTaskStatus.PENDING);
        DocumentStorageCleanupMessage message = new ObjectMapper().findAndRegisterModules().readValue(
                outbox.getMessageBody(), DocumentStorageCleanupMessage.class);
        assertThat(message.outboxId()).isEqualTo(outbox.getOutboxId());
        assertThat(message.originalObjectName()).isEqualTo("original/demo.pdf");
        assertThat(message.parsedObjectName()).isEqualTo("parsed/demo.md");
        assertThat(message.schemaVersion()).isEqualTo(2);
        assertThat(message.parsedObjectPrefix()).isEqualTo("parsed/1/");
        assertThat(message.sourceSnapshotPrefix()).isEqualTo("source-snapshots/1/");
    }
}
