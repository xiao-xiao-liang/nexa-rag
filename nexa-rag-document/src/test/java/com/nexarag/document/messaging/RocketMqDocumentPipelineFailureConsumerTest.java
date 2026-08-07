package com.nexarag.document.messaging.consumer;

import com.nexarag.document.service.impl.DocumentProcessFailureService;
import com.nexarag.infra.messaging.document.model.DocumentPipelineFailureMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * RocketMQ文档流水线失败消费者测试，验证最终失败委托行为。
 */
class RocketMqDocumentPipelineFailureConsumerTest {

    @Test
    void shouldDelegateFinalFailureTransaction() {
        DocumentProcessFailureService failureService = mock(DocumentProcessFailureService.class);
        RocketMqDocumentPipelineFailureConsumer consumer =
                new RocketMqDocumentPipelineFailureConsumer(failureService);
        DocumentPipelineFailureMessage message = new DocumentPipelineFailureMessage(
                1L, "process-1", "INDEXING", "索引失败", "detail", 6, "message-1", LocalDateTime.now());

        consumer.onMessage(message);

        verify(failureService).markFinalFailure(message);
    }
}
