package com.nexarag.document.messaging.consumer;

import com.nexarag.document.service.impl.DocumentProcessFailureService;
import com.nexarag.document.constants.DocumentMessagingConstants;
import com.nexarag.infra.messaging.document.model.DocumentPipelineFailureMessage;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RocketMQ文档流水线失败消费者，负责提交最终失败状态并触发告警。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.document.pipeline.messaging", name = "type", havingValue = "rocketmq")
@RocketMQMessageListener(
        topic = DocumentMessagingConstants.PIPELINE_FAILURE_TOPIC,
        consumerGroup = DocumentMessagingConstants.PIPELINE_FAILURE_CONSUMER_GROUP)
public class RocketMqDocumentPipelineFailureConsumer
        implements RocketMQListener<DocumentPipelineFailureMessage> {

    private final DocumentProcessFailureService failureService;

    @Override
    public void onMessage(DocumentPipelineFailureMessage message) {
        // 1. 使用独立事务处理最终失败，异常继续抛出以触发RocketMQ重试
        failureService.markFinalFailure(message);
    }
}
