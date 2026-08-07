package com.nexarag.retrieval.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentTaskAlertService;
import com.nexarag.infra.messaging.document.task.DocumentTaskMessage;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 索引清理死信消费者测试，验证父任务失败后创建渠道告警任务。
 */
class DocumentIndexCleanupDeadLetterConsumerTest {

    @Test
    void shouldCreateAlertTasksAfterMarkingCleanupTaskFailed() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentTaskAlertService alertService = mock(DocumentTaskAlertService.class);
        DocumentTaskMessage taskMessage = new DocumentTaskMessage(101L, 1L, null, "operation-1",
                "CLEAN_DOCUMENT_INDEX", 1, LocalDateTime.of(2026, 8, 7, 18, 0));
        when(objectMapper.readValue(any(byte[].class), eq(DocumentTaskMessage.class))).thenReturn(taskMessage);
        when(outboxService.markTaskFailed(eq(101L), eq(6), any())).thenReturn(true);
        DocumentIndexCleanupDeadLetterConsumer consumer = new DocumentIndexCleanupDeadLetterConsumer(objectMapper,
                outboxService, alertService);
        MessageExt messageExt = new MessageExt();
        messageExt.setBody("{}".getBytes(StandardCharsets.UTF_8));
        messageExt.setReconsumeTimes(5);

        consumer.onMessage(messageExt);

        verify(alertService).createFailureAlerts(101L, 6, "文档索引清理任务进入RocketMQ死信队列");
    }
}
