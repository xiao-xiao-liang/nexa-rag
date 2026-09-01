package com.nexarag.document.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.messaging.consumer.RocketMqDocumentPipelineDeadLetterConsumer;
import com.nexarag.document.service.impl.DocumentProcessFailureService;
import com.nexarag.infra.messaging.document.model.DocumentPipelineFailureMessage;
import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * RocketMQ文档流水线死信消费者测试，验证死信消费不会增加实际Workflow执行次数。
 */
class RocketMqDocumentPipelineDeadLetterConsumerTest {

    @Test
    void shouldKeepLastWorkflowConsumptionCountWhenMessageEntersDeadLetterQueue() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DocumentProcessFailureService failureService = mock(DocumentProcessFailureService.class);
        RocketMqDocumentPipelineDeadLetterConsumer consumer =
                new RocketMqDocumentPipelineDeadLetterConsumer(objectMapper, failureService);
        MessageExt messageExt = new MessageExt();
        messageExt.setMsgId("message-1");
        messageExt.setReconsumeTimes(6);
        messageExt.setBody(objectMapper.writeValueAsBytes(
                new DocumentPipelineMessage(1L, 2L, "process-1", 101L, 2, LocalDateTime.now())));

        consumer.onMessage(messageExt);

        ArgumentCaptor<DocumentPipelineFailureMessage> captor =
                ArgumentCaptor.forClass(DocumentPipelineFailureMessage.class);
        verify(failureService).markFinalFailure(captor.capture());
        assertThat(captor.getValue().documentVersionId()).isEqualTo(2L);
        assertThat(captor.getValue().consumedTimes()).isEqualTo(6);
        assertThat(captor.getValue().messageId()).isEqualTo("message-1");
    }
}
