package com.nexarag.document.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.messaging.consumer.RocketMqDocumentStorageCleanupDeadLetterConsumer;
import com.nexarag.document.service.DocumentTaskFinalFailureService;
import com.nexarag.infra.messaging.document.task.DocumentStorageCleanupMessage;
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
 * 对象存储清理死信消费者测试。
 */
class RocketMqDocumentStorageCleanupDeadLetterConsumerTest {

    @Test
    void shouldCreateAlertTasksAfterStorageCleanupTaskReachesDeadLetterQueue() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        DocumentTaskFinalFailureService finalFailureService = mock(DocumentTaskFinalFailureService.class);
        DocumentStorageCleanupMessage taskMessage = new DocumentStorageCleanupMessage(101L, 1L, "operation-1",
                "CLEAN_DOCUMENT_STORAGE", 1, "original/demo.pdf", "parsed/demo.md",
                LocalDateTime.of(2026, 8, 9, 18, 30));
        when(objectMapper.readValue(any(byte[].class), eq(DocumentStorageCleanupMessage.class))).thenReturn(taskMessage);
        RocketMqDocumentStorageCleanupDeadLetterConsumer consumer = new RocketMqDocumentStorageCleanupDeadLetterConsumer(
                objectMapper, finalFailureService);
        MessageExt messageExt = new MessageExt();
        messageExt.setBody("{}".getBytes(StandardCharsets.UTF_8));
        messageExt.setReconsumeTimes(5);

        consumer.onMessage(messageExt);

        verify(finalFailureService).markFailedAndCreateAlerts(101L, 6, "文档对象存储清理任务进入RocketMQ死信队列");
    }
}
