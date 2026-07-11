package com.nexarag.document.outbox.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.outbox.config.DocumentPipelineOutboxProperties;
import com.nexarag.document.outbox.entity.DocumentPipelineOutbox;
import com.nexarag.document.outbox.service.DocumentPipelineOutboxService;
import com.nexarag.infra.messaging.document.DocumentPipelineMessagePublisher;
import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import com.nexarag.infra.messaging.document.model.DocumentPipelinePublishResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档流水线Outbox发布器测试。
 */
class DocumentPipelineOutboxPublisherTest {

    @Test
    void publishPendingMessagesShouldMarkPublished() throws Exception {
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentPipelineMessagePublisher messagePublisher = mock(DocumentPipelineMessagePublisher.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DocumentPipelineMessage message = new DocumentPipelineMessage(1L, "process-1", 1, LocalDateTime.now());
        DocumentPipelineOutbox outbox = DocumentPipelineOutbox.builder()
                .outboxId(10L)
                .documentId(1L)
                .processId("process-1")
                .messageBody(objectMapper.writeValueAsString(message))
                .build();
        when(outboxService.claimPublishableMessages(any(), any())).thenReturn(List.of(outbox));
        when(messagePublisher.publish(any())).thenReturn(DocumentPipelinePublishResult.success("message-1"));
        DocumentPipelineOutboxPublisher publisher = new DocumentPipelineOutboxPublisher(
                outboxService, messagePublisher, objectMapper, properties());

        publisher.publishPendingMessages();

        verify(outboxService).markPublished(10L);
    }

    @Test
    void publishPendingMessagesShouldRecordFailureWithoutInterruptingBatch() throws Exception {
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentPipelineMessagePublisher messagePublisher = mock(DocumentPipelineMessagePublisher.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DocumentPipelineMessage message = new DocumentPipelineMessage(1L, "process-1", 1, LocalDateTime.now());
        DocumentPipelineOutbox failed = DocumentPipelineOutbox.builder()
                .outboxId(10L).documentId(1L).processId("process-1")
                .messageBody(objectMapper.writeValueAsString(message)).build();
        DocumentPipelineOutbox succeeded = DocumentPipelineOutbox.builder()
                .outboxId(11L).documentId(1L).processId("process-1")
                .messageBody(objectMapper.writeValueAsString(message)).build();
        when(outboxService.claimPublishableMessages(any(), any())).thenReturn(List.of(failed, succeeded));
        when(messagePublisher.publish(any()))
                .thenThrow(new IllegalStateException("测试发送失败"))
                .thenReturn(DocumentPipelinePublishResult.success("message-2"));
        doThrow(new IllegalStateException("测试状态更新失败"))
                .when(outboxService).markPublishFailed(10L, "测试发送失败");
        DocumentPipelineOutboxPublisher publisher = new DocumentPipelineOutboxPublisher(
                outboxService, messagePublisher, objectMapper, properties());

        publisher.publishPendingMessages();

        verify(outboxService).markPublishFailed(10L, "测试发送失败");
        verify(outboxService).markPublished(11L);
    }

    private DocumentPipelineOutboxProperties properties() {
        DocumentPipelineOutboxProperties properties = new DocumentPipelineOutboxProperties();
        properties.setBatchSize(100);
        return properties;
    }
}
