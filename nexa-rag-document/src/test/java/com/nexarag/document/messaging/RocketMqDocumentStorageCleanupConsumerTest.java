package com.nexarag.document.messaging;

import com.nexarag.document.messaging.consumer.RocketMqDocumentStorageCleanupConsumer;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentVersionCleanupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.infra.messaging.document.task.DocumentStorageCleanupMessage;
import com.nexarag.infra.messaging.document.task.DocumentVersionStorageCleanupMessage;
import com.nexarag.infra.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.rocketmq.common.message.MessageExt;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * 文档对象存储清理消费者测试。
 */
class RocketMqDocumentStorageCleanupConsumerTest {

    @Test
    void shouldMarkFullDependencyConstructorForSpringInjection() {
        assertThat(Arrays.stream(RocketMqDocumentStorageCleanupConsumer.class.getConstructors())
                .filter(constructor -> constructor.getParameterCount() == 4)
                .anyMatch(constructor -> constructor.isAnnotationPresent(Autowired.class))).isTrue();
    }

    @Test
    void shouldDeleteDistinctOriginalAndParsedObjectsBeforeCompletingTask() throws Exception {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(outboxService.markTaskProcessing(101L, 1)).thenReturn(true);
        RocketMqDocumentStorageCleanupConsumer consumer = new RocketMqDocumentStorageCleanupConsumer(
                objectMapper, fileStorageService, outboxService);
        DocumentStorageCleanupMessage message = new DocumentStorageCleanupMessage(101L, 1L, "operation-1",
                "CLEAN_DOCUMENT_STORAGE", 1, "original/demo.pdf", "parsed/demo.md",
                LocalDateTime.of(2026, 8, 9, 18, 30));
        MessageExt messageExt = messageExt(0);
        when(objectMapper.readValue(any(byte[].class), eq(DocumentStorageCleanupMessage.class))).thenReturn(message);

        consumer.onMessage(messageExt);

        verify(fileStorageService).delete("original/demo.pdf");
        verify(fileStorageService).delete("parsed/demo.md");
        verify(outboxService).markTaskSucceeded(101L);
    }

    @Test
    void shouldLeaveTaskProcessingForRocketMqRetryWhenObjectDeletionFails() throws Exception {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(outboxService.markTaskProcessing(101L, 1)).thenReturn(true);
        doThrow(new IllegalStateException("MinIO不可用")).when(fileStorageService).delete("original/demo.pdf");
        RocketMqDocumentStorageCleanupConsumer consumer = new RocketMqDocumentStorageCleanupConsumer(
                objectMapper, fileStorageService, outboxService);

        DocumentStorageCleanupMessage message = new DocumentStorageCleanupMessage(101L, 1L, "operation-1",
                "CLEAN_DOCUMENT_STORAGE", 1, "original/demo.pdf", "parsed/demo.md",
                LocalDateTime.of(2026, 8, 9, 18, 30));

        when(objectMapper.readValue(any(byte[].class), eq(DocumentStorageCleanupMessage.class))).thenReturn(message);

        assertThatThrownBy(() -> consumer.onMessage(messageExt(0))).isInstanceOf(IllegalStateException.class);

        verify(fileStorageService).delete("original/demo.pdf");
        verify(fileStorageService, never()).delete("parsed/demo.md");
        verify(outboxService, never()).markTaskSucceeded(101L);
    }

    @Test
    void shouldDeleteSameObjectOnlyOnce() throws Exception {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(outboxService.markTaskProcessing(101L, 1)).thenReturn(true);
        RocketMqDocumentStorageCleanupConsumer consumer = new RocketMqDocumentStorageCleanupConsumer(
                objectMapper, fileStorageService, outboxService);
        DocumentStorageCleanupMessage message = new DocumentStorageCleanupMessage(101L, 1L, "operation-1",
                "CLEAN_DOCUMENT_STORAGE", 1, "original/demo.pdf", "original/demo.pdf",
                LocalDateTime.of(2026, 8, 9, 18, 30));
        when(objectMapper.readValue(any(byte[].class), eq(DocumentStorageCleanupMessage.class))).thenReturn(message);

        consumer.onMessage(messageExt(0));

        verify(fileStorageService).delete("original/demo.pdf");
        verify(outboxService).markTaskSucceeded(101L);
    }

    @Test
    void shouldDeleteValidatedPrefixesForSchemaVersionTwo() throws Exception {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(outboxService.markTaskProcessing(101L, 1)).thenReturn(true);
        DocumentStorageCleanupMessage message = new DocumentStorageCleanupMessage(101L, 1L, "operation-1",
                "CLEAN_DOCUMENT_STORAGE", 2, "original/demo.docx", "parsed/1/content.md",
                "parsed/1/", "source-snapshots/1/", LocalDateTime.of(2026, 8, 9, 18, 30));
        when(objectMapper.readValue(any(byte[].class), eq(DocumentStorageCleanupMessage.class))).thenReturn(message);
        RocketMqDocumentStorageCleanupConsumer consumer = new RocketMqDocumentStorageCleanupConsumer(
                objectMapper, fileStorageService, outboxService);

        consumer.onMessage(messageExt(0));

        verify(fileStorageService).delete("original/demo.docx");
        verify(fileStorageService).deleteByPrefix("parsed/1/");
        verify(fileStorageService).deleteByPrefix("source-snapshots/1/");
        verify(fileStorageService, never()).delete("parsed/1/content.md");
        verify(outboxService).markTaskSucceeded(101L);
    }

    @Test
    void shouldRecordActualConsumptionTimesWhenMessageIsRedelivered() throws Exception {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(outboxService.markTaskProcessing(101L, 2)).thenReturn(true);
        when(objectMapper.readValue(any(byte[].class), eq(DocumentStorageCleanupMessage.class))).thenReturn(
                new DocumentStorageCleanupMessage(101L, 1L, "operation-1", "CLEAN_DOCUMENT_STORAGE", 1,
                        "original/demo.pdf", null, LocalDateTime.of(2026, 8, 9, 18, 30)));
        RocketMqDocumentStorageCleanupConsumer consumer = new RocketMqDocumentStorageCleanupConsumer(
                objectMapper, fileStorageService, outboxService);

        consumer.onMessage(messageExt(1));

        verify(outboxService).markTaskProcessing(101L, 2);
    }

    @Test
    void shouldDeleteOnlyVersionObjectsThenPhysicallyCleanupVersionData() throws Exception {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentVersionCleanupService versionCleanupService = mock(DocumentVersionCleanupService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(outboxService.markTaskProcessing(101L, 1)).thenReturn(true);
        when(objectMapper.readValue(any(byte[].class), eq(DocumentVersionStorageCleanupMessage.class))).thenReturn(
                new DocumentVersionStorageCleanupMessage(101L, 1L, 2L, "operation-1",
                        "CLEAN_DOCUMENT_VERSION_STORAGE", 1, "original/v2.pdf", "parsed/v2.md",
                        LocalDateTime.of(2026, 8, 30, 21, 0)));
        when(objectMapper.readTree(any(byte[].class))).thenReturn(new ObjectMapper().readTree("{\"documentVersionId\":2}"));
        RocketMqDocumentStorageCleanupConsumer consumer = new RocketMqDocumentStorageCleanupConsumer(objectMapper,
                fileStorageService, outboxService, versionCleanupService);
        MessageExt messageExt = new MessageExt();
        messageExt.setBody("{\"documentVersionId\":2}".getBytes(StandardCharsets.UTF_8));

        consumer.onMessage(messageExt);

        verify(fileStorageService).delete("original/v2.pdf");
        verify(fileStorageService).delete("parsed/v2.md");
        verify(versionCleanupService).cleanup(1L, 2L);
        verify(outboxService).markTaskSucceeded(101L);
    }

    private MessageExt messageExt(int reconsumeTimes) {
        MessageExt messageExt = new MessageExt();
        messageExt.setBody("{}".getBytes(StandardCharsets.UTF_8));
        messageExt.setReconsumeTimes(reconsumeTimes);
        return messageExt;
    }
}
