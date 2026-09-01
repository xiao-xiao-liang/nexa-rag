package com.nexarag.retrieval.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentDeleteTaskService;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.infra.messaging.document.task.DocumentVersionIndexCleanupMessage;
import com.nexarag.retrieval.service.DocumentVersionIndexCleaner;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 文档索引清理消费者测试。 */
class DocumentIndexCleanupConsumerTest {

    @Test
    void shouldCleanVersionIndexThenCreateVersionStorageCleanupTask() throws Exception {
        DocumentVersionIndexCleaner versionIndexCleaner = mock(DocumentVersionIndexCleaner.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentVersionService versionService = mock(DocumentVersionService.class);
        DocumentDeleteTaskService deleteTaskService = mock(DocumentDeleteTaskService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        DocumentIndexCleanupConsumer consumer = new DocumentIndexCleanupConsumer(versionIndexCleaner, outboxService,
                versionService, deleteTaskService, objectMapper);
        when(outboxService.markTaskProcessing(101L, 1)).thenReturn(true);
        when(objectMapper.readValue(any(byte[].class), eq(DocumentVersionIndexCleanupMessage.class))).thenReturn(
                new DocumentVersionIndexCleanupMessage(101L, 1L, 2L, "operation-1",
                        "CLEAN_DOCUMENT_VERSION_INDEX", 1, LocalDateTime.of(2026, 8, 30, 21, 0)));
        DocumentVersionDO deletingVersion = DocumentVersionDO.builder().documentId(1L).documentVersionId(2L)
                .status(DocumentVersionStatus.DELETING).build();
        when(versionService.getRequiredVersion(1L, 2L)).thenReturn(deletingVersion);
        MessageExt messageExt = new MessageExt();
        messageExt.setBody("{\"documentVersionId\":2}".getBytes(StandardCharsets.UTF_8));

        consumer.onMessage(messageExt);

        verify(versionIndexCleaner).cleanup(1L, 2L);
        verify(deleteTaskService).createVersionStorageCleanupTask(deletingVersion);
        verify(outboxService).markTaskSucceeded(101L);
    }

    @Test
    void shouldRejectDocumentOnlyCleanupMessage() {
        DocumentIndexCleanupConsumer consumer = new DocumentIndexCleanupConsumer(mock(DocumentVersionIndexCleaner.class),
                mock(DocumentPipelineOutboxService.class), mock(DocumentVersionService.class),
                mock(DocumentDeleteTaskService.class), new ObjectMapper());
        MessageExt messageExt = new MessageExt();
        messageExt.setBody("{}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> consumer.onMessage(messageExt))
                .isInstanceOf(com.nexarag.common.exception.ServiceException.class)
                .hasMessageContaining("缺少文档版本边界");
    }
}
