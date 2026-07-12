package com.nexarag.infra.messaging.document.rocketmq;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.messaging.document.DocumentPipelineMessagePublisher;
import com.nexarag.infra.config.DocumentPipelineMessagingProperties;
import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import com.nexarag.infra.messaging.document.model.DocumentPipelinePublishResult;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.messaging.Message;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RocketMQ 文档流水线发布器测试，验证消息发送结果、异常转换和条件装配行为。
 */
class RocketMqDocumentPipelinePublisherTest {

    private static final String TOPIC = "document-pipeline-topic";
    private static final Long DOCUMENT_ID = 1001L;
    private static final String PROCESS_ID = "process-001";

    @Test
    void shouldReturnSuccessResultAndSendExpectedTopicAndKey() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getSendStatus()).thenReturn(SendStatus.SEND_OK);
        when(sendResult.getMsgId()).thenReturn("message-001");
        when(rocketMQTemplate.syncSend(eq(TOPIC), any(Message.class))).thenReturn(sendResult);
        RocketMqDocumentPipelinePublisher publisher = createPublisher(rocketMQTemplate);

        DocumentPipelinePublishResult result = publisher.publish(createMessage());

        assertThat(result.success()).isTrue();
        assertThat(result.messageId()).isEqualTo("message-001");
        assertThat(result.failureReason()).isNull();
        ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(eq(TOPIC), messageCaptor.capture());
        assertThat(messageCaptor.getValue().getPayload()).isEqualTo(createMessage());
        assertThat(messageCaptor.getValue().getHeaders().get(RocketMQHeaders.KEYS))
                .isEqualTo(DOCUMENT_ID + ":" + PROCESS_ID);
    }

    @Test
    void shouldThrowServiceExceptionWhenSendStatusIsNotOk() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getSendStatus()).thenReturn(SendStatus.FLUSH_DISK_TIMEOUT);
        when(rocketMQTemplate.syncSend(eq(TOPIC), any(Message.class))).thenReturn(sendResult);
        RocketMqDocumentPipelinePublisher publisher = createPublisher(rocketMQTemplate);

        assertThatThrownBy(() -> publisher.publish(createMessage()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文档流水线消息发布失败")
                .hasMessageContaining("documentId=" + DOCUMENT_ID)
                .hasMessageContaining("processId=" + PROCESS_ID)
                .hasMessageContaining(SendStatus.FLUSH_DISK_TIMEOUT.name());
    }

    @Test
    void shouldThrowServiceExceptionWhenSendResultIsNull() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        when(rocketMQTemplate.syncSend(eq(TOPIC), any(Message.class))).thenReturn(null);
        RocketMqDocumentPipelinePublisher publisher = createPublisher(rocketMQTemplate);

        assertThatThrownBy(() -> publisher.publish(createMessage()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("发送结果为空")
                .hasMessageContaining("documentId=" + DOCUMENT_ID)
                .hasMessageContaining("processId=" + PROCESS_ID);
    }

    @Test
    void shouldKeepOriginalExceptionWhenSyncSendThrowsException() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        IllegalStateException originalException = new IllegalStateException("broker unavailable");
        when(rocketMQTemplate.syncSend(eq(TOPIC), any(Message.class))).thenThrow(originalException);
        RocketMqDocumentPipelinePublisher publisher = createPublisher(rocketMQTemplate);

        assertThatThrownBy(() -> publisher.publish(createMessage()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("调用 RocketMQ 发布文档流水线消息异常")
                .hasMessageContaining("documentId=" + DOCUMENT_ID)
                .hasMessageContaining("processId=" + PROCESS_ID)
                .hasCause(originalException);
    }

    @Test
    void shouldThrowServiceExceptionWhenMessageIsNull() {
        RocketMqDocumentPipelinePublisher publisher = createPublisher(mock(RocketMQTemplate.class));

        assertThatThrownBy(() -> publisher.publish(null))
                .isInstanceOf(ServiceException.class)
                .hasMessage("文档流水线消息不能为空");
    }

    @Test
    void shouldThrowServiceExceptionWhenMessageIdIsBlank() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getSendStatus()).thenReturn(SendStatus.SEND_OK);
        when(sendResult.getMsgId()).thenReturn(" ");
        when(rocketMQTemplate.syncSend(eq(TOPIC), any(Message.class))).thenReturn(sendResult);
        RocketMqDocumentPipelinePublisher publisher = createPublisher(rocketMQTemplate);

        assertThatThrownBy(() -> publisher.publish(createMessage()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("消息ID为空")
                .hasMessageContaining("documentId=" + DOCUMENT_ID)
                .hasMessageContaining("processId=" + PROCESS_ID);
    }

    @Test
    void shouldCreatePublisherOnlyForRocketMqOutboxMode() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        new ApplicationContextRunner()
                .withBean(RocketMQTemplate.class, () -> rocketMQTemplate)
                .withBean(DocumentPipelineMessagingProperties.class)
                .withUserConfiguration(RocketMqDocumentPipelinePublisher.class)
                .withPropertyValues(
                        "nexa.document.pipeline.messaging.type=rocketmq",
                        "nexa.document.pipeline.messaging.publish-mode=outbox")
                .run(context -> assertThat(context).hasSingleBean(DocumentPipelineMessagePublisher.class));
    }

    @Test
    void shouldNotCreatePublisherForNonOutboxMode() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        new ApplicationContextRunner()
                .withBean(RocketMQTemplate.class, () -> rocketMQTemplate)
                .withBean(DocumentPipelineMessagingProperties.class)
                .withUserConfiguration(RocketMqDocumentPipelinePublisher.class)
                .withPropertyValues(
                        "nexa.document.pipeline.messaging.type=rocketmq",
                        "nexa.document.pipeline.messaging.publish-mode=direct")
                .run(context -> assertThat(context).doesNotHaveBean(DocumentPipelineMessagePublisher.class));
    }

    @Test
    void shouldNotCreatePublisherForNonRocketMqType() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        new ApplicationContextRunner()
                .withBean(RocketMQTemplate.class, () -> rocketMQTemplate)
                .withBean(DocumentPipelineMessagingProperties.class)
                .withUserConfiguration(RocketMqDocumentPipelinePublisher.class)
                .withPropertyValues(
                        "nexa.document.pipeline.messaging.type=kafka",
                        "nexa.document.pipeline.messaging.publish-mode=outbox")
                .run(context -> assertThat(context).doesNotHaveBean(DocumentPipelineMessagePublisher.class));
    }

    private RocketMqDocumentPipelinePublisher createPublisher(RocketMQTemplate rocketMQTemplate) {
        DocumentPipelineMessagingProperties properties = new DocumentPipelineMessagingProperties();
        properties.setTopic(TOPIC);
        return new RocketMqDocumentPipelinePublisher(rocketMQTemplate, properties);
    }

    private DocumentPipelineMessage createMessage() {
        return new DocumentPipelineMessage(DOCUMENT_ID, PROCESS_ID, 1,
                LocalDateTime.of(2026, 7, 11, 10, 0));
    }
}
