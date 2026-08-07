package com.nexarag.retrieval.messaging;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.infra.messaging.document.task.DocumentTaskMessage;
import com.nexarag.retrieval.dto.res.DocumentIndexCleanupResult;
import com.nexarag.retrieval.service.DocumentIndexCleaner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 文档索引清理消费者，按文档ID幂等清理 Milvus 和 Elasticsearch 派生索引。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "${nexa.document.task.cleanup-topic:nexa-document-index-cleanup}",
        consumerGroup = "${nexa.document.task.cleanup-consumer-group:nexa-document-index-cleanup-worker}",
        maxReconsumeTimes = 5)
public class DocumentIndexCleanupConsumer implements RocketMQListener<DocumentTaskMessage> {

    private final DocumentIndexCleaner documentIndexCleaner;
    private final DocumentPipelineOutboxService outboxService;

    @Override
    public void onMessage(DocumentTaskMessage message) {
        // 1. 校验消息并领取任务，终态重复消息直接确认
        if (message == null || message.outboxId() == null || message.documentId() == null) {
            throw new ServiceException("文档索引清理消息不完整");
        }
        int consumeTimes = 1;
        if (!outboxService.markTaskProcessing(message.outboxId(), consumeTimes)) {
            return;
        }

        // 2. 清理三类外部索引，任一失败均交由RocketMQ重试
        DocumentIndexCleanupResult result = documentIndexCleaner.cleanup(message.documentId());
        if (!result.success()) {
            throw new ServiceException("文档索引清理失败，documentId=" + message.documentId()
                    + "，原因=" + result.failureReason());
        }
        outboxService.markTaskSucceeded(message.outboxId());
        log.info("文档外部索引清理完成，outboxId={}，documentId={}，operationId={}",
                message.outboxId(), message.documentId(), message.operationId());
    }
}
