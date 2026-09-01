package com.nexarag.document.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ClientException;
import com.nexarag.document.messaging.consumer.RocketMqDocumentPipelineConsumer;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.impl.DocumentProcessFailureService;
import com.nexarag.infra.messaging.document.DocumentPipelineMessageHandler;
import com.nexarag.infra.config.DocumentPipelineMessagingProperties;
import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RocketMQ文档流水线消费者测试，验证轮次幂等和异常分类行为。
 */
class RocketMqDocumentPipelineConsumerTest {

    @Test
    void shouldRecordConsumptionAndDelegateCurrentProcess() throws Exception {
        Fixture fixture = fixture();
        when(fixture.documentVersionService.recordMessageConsumption(1L, 2L, "process-1", "message-1", 2))
                .thenReturn(true);
        when(fixture.outboxService.markTaskProcessing(101L, 2)).thenReturn(true);

        fixture.consumer.onMessage(messageExt(fixture.objectMapper, 1));

        verify(fixture.outboxService).markTaskProcessing(101L, 2);
        verify(fixture.messageHandler).handle(any(DocumentPipelineMessage.class));
        verify(fixture.documentVersionService).markMessageCompleted(1L, 2L, "process-1");
        verify(fixture.outboxService).markTaskSucceeded(101L);
    }

    @Test
    void shouldAckOldProcessWithoutCallingHandler() throws Exception {
        Fixture fixture = fixture();
        when(fixture.documentVersionService.recordMessageConsumption(any(), any(), anyString(), anyString(), any(Integer.class)))
                .thenReturn(false);

        fixture.consumer.onMessage(messageExt(fixture.objectMapper, 0));

        verify(fixture.messageHandler, never()).handle(any());
    }

    @Test
    void shouldRethrowRetryableException() throws Exception {
        Fixture fixture = fixture();
        when(fixture.documentVersionService.recordMessageConsumption(any(), any(), anyString(), anyString(), any(Integer.class)))
                .thenReturn(true);
        IllegalStateException failure = new IllegalStateException("临时网络异常");
        doThrow(failure).when(fixture.messageHandler).handle(any());

        assertThatThrownBy(() -> fixture.consumer.onMessage(messageExt(fixture.objectMapper, 0)))
                .isSameAs(failure);
        verify(fixture.failureService).recordFailure(1L, 2L, "process-1", "PARSING", "临时网络异常", failure.toString());
    }

    @Test
    void shouldPublishFailureTopicForNonRetryableException() throws Exception {
        Fixture fixture = fixture();
        when(fixture.documentVersionService.recordMessageConsumption(any(), any(), anyString(), anyString(), any(Integer.class)))
                .thenReturn(true);
        doThrow(new ClientException("处理配置非法")).when(fixture.messageHandler).handle(any());
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);
        doReturn(sendResult).when(fixture.rocketMQTemplate).syncSend(anyString(), any(org.springframework.messaging.Message.class));

        fixture.consumer.onMessage(messageExt(fixture.objectMapper, 0));

        verify(fixture.rocketMQTemplate).syncSend(
                org.mockito.ArgumentMatchers.eq(fixture.properties.getFailureTopic()),
                any(org.springframework.messaging.Message.class));
    }

    private Fixture fixture() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        DocumentPipelineMessageHandler messageHandler = mock(DocumentPipelineMessageHandler.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        DocumentProcessFailureService failureService = mock(DocumentProcessFailureService.class);
        DocumentPipelineMessagingProperties properties = new DocumentPipelineMessagingProperties();
        when(outboxService.markTaskProcessing(any(Long.class), anyInt())).thenReturn(true);
        when(documentVersionService.getRequiredVersion(1L, 2L)).thenReturn(DocumentVersionDO.builder()
                .documentId(1L)
                .documentVersionId(2L)
                .status(DocumentVersionStatus.PARSING)
                .build());
        RocketMqDocumentPipelineConsumer consumer = new RocketMqDocumentPipelineConsumer(
                objectMapper, documentVersionService, outboxService, messageHandler, rocketMQTemplate, properties, failureService);
        return new Fixture(objectMapper, documentVersionService, outboxService, messageHandler, rocketMQTemplate, properties,
                failureService, consumer);
    }

    private MessageExt messageExt(ObjectMapper objectMapper, int reconsumeTimes) throws Exception {
        MessageExt messageExt = new MessageExt();
        messageExt.setMsgId("message-1");
        messageExt.setReconsumeTimes(reconsumeTimes);
        messageExt.setBody(objectMapper.writeValueAsBytes(
                new DocumentPipelineMessage(1L, 2L, "process-1", 101L, 2, LocalDateTime.now())));
        return messageExt;
    }

    private record Fixture(ObjectMapper objectMapper,
                           DocumentVersionService documentVersionService,
                           DocumentPipelineOutboxService outboxService,
                           DocumentPipelineMessageHandler messageHandler,
                           RocketMQTemplate rocketMQTemplate,
                           DocumentPipelineMessagingProperties properties,
                           DocumentProcessFailureService failureService,
                           RocketMqDocumentPipelineConsumer consumer) {
    }
}
