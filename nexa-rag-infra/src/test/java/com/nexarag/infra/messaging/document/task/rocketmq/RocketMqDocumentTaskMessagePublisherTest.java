package com.nexarag.infra.messaging.document.task.rocketmq;

import com.nexarag.infra.messaging.document.task.DocumentMessagePublishResult;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通用文档任务 RocketMQ 发布器测试。
 */
class RocketMqDocumentTaskMessagePublisherTest {

    @Test
    void publishShouldUseCallerProvidedTopicAndMessageKey() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getSendStatus()).thenReturn(SendStatus.SEND_OK);
        when(sendResult.getMsgId()).thenReturn("message-1");
        when(rocketMQTemplate.syncSend(eq("nexa-document-index-cleanup"), any(Message.class)))
                .thenReturn(sendResult);
        RocketMqDocumentTaskMessagePublisher publisher = new RocketMqDocumentTaskMessagePublisher(rocketMQTemplate);

        DocumentMessagePublishResult result = publisher.publish("nexa-document-index-cleanup",
                "6:CLEAN_DOCUMENT_INDEX:operation-1", new TestPayload(6L));

        assertThat(result.messageId()).isEqualTo("message-1");
        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(eq("nexa-document-index-cleanup"), captor.capture());
        assertThat(captor.getValue().getHeaders().get(RocketMQHeaders.KEYS))
                .isEqualTo("6:CLEAN_DOCUMENT_INDEX:operation-1");
    }

    private record TestPayload(Long documentId) {
    }
}
