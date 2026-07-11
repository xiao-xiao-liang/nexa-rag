package com.nexarag.document.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ClientException;
import com.nexarag.document.service.DocumentService;
import com.nexarag.infra.messaging.document.DocumentPipelineMessageHandler;
import com.nexarag.infra.messaging.document.config.DocumentPipelineMessagingProperties;
import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        when(fixture.documentService.recordMessageConsumption(1L, "process-1", "message-1", 2))
                .thenReturn(true);

        fixture.consumer.onMessage(messageExt(fixture.objectMapper, 1));

        verify(fixture.messageHandler).handle(any(DocumentPipelineMessage.class));
    }

    @Test
    void shouldAckOldProcessWithoutCallingHandler() throws Exception {
        Fixture fixture = fixture();
        when(fixture.documentService.recordMessageConsumption(any(), anyString(), anyString(), any(Integer.class)))
                .thenReturn(false);

        fixture.consumer.onMessage(messageExt(fixture.objectMapper, 0));

        verify(fixture.messageHandler, never()).handle(any());
    }

    @Test
    void shouldRethrowRetryableException() throws Exception {
        Fixture fixture = fixture();
        when(fixture.documentService.recordMessageConsumption(any(), anyString(), anyString(), any(Integer.class)))
                .thenReturn(true);
        IllegalStateException failure = new IllegalStateException("临时网络异常");
        doThrow(failure).when(fixture.messageHandler).handle(any());

        assertThatThrownBy(() -> fixture.consumer.onMessage(messageExt(fixture.objectMapper, 0)))
                .isSameAs(failure);
    }

    @Test
    void shouldPublishFailureTopicForNonRetryableException() throws Exception {
        Fixture fixture = fixture();
        when(fixture.documentService.recordMessageConsumption(any(), anyString(), anyString(), any(Integer.class)))
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
        DocumentService documentService = mock(DocumentService.class);
        DocumentPipelineMessageHandler messageHandler = mock(DocumentPipelineMessageHandler.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        DocumentPipelineMessagingProperties properties = new DocumentPipelineMessagingProperties();
        RocketMqDocumentPipelineConsumer consumer = new RocketMqDocumentPipelineConsumer(
                objectMapper, documentService, messageHandler, rocketMQTemplate, properties);
        return new Fixture(objectMapper, documentService, messageHandler, rocketMQTemplate, properties, consumer);
    }

    private MessageExt messageExt(ObjectMapper objectMapper, int reconsumeTimes) throws Exception {
        MessageExt messageExt = new MessageExt();
        messageExt.setMsgId("message-1");
        messageExt.setReconsumeTimes(reconsumeTimes);
        messageExt.setBody(objectMapper.writeValueAsBytes(
                new DocumentPipelineMessage(1L, "process-1", 1, LocalDateTime.now())));
        return messageExt;
    }

    private record Fixture(ObjectMapper objectMapper,
                           DocumentService documentService,
                           DocumentPipelineMessageHandler messageHandler,
                           RocketMQTemplate rocketMQTemplate,
                           DocumentPipelineMessagingProperties properties,
                           RocketMqDocumentPipelineConsumer consumer) {
    }
}
