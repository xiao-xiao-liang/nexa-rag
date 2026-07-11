package com.nexarag.document.outbox.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.outbox.config.DocumentPipelineOutboxProperties;
import com.nexarag.document.outbox.entity.DocumentPipelineOutbox;
import com.nexarag.document.outbox.service.DocumentPipelineOutboxService;
import com.nexarag.infra.messaging.document.DocumentPipelineMessagePublisher;
import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 文档流水线Outbox发布任务，负责抢占数据库消息并发布到已配置的消息中间件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.document.pipeline.messaging", name = "publish-mode",
        havingValue = "outbox", matchIfMissing = true)
public class DocumentPipelineOutboxPublisher {

    private final DocumentPipelineOutboxService outboxService;
    private final DocumentPipelineMessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;
    private final DocumentPipelineOutboxProperties properties;
    private final String lockOwner = UUID.randomUUID().toString();

    /**
     * 扫描并发布待处理的Outbox消息。
     */
    @Scheduled(fixedDelayString = "${nexa.document.pipeline.outbox.poll-interval-ms:1000}")
    public void publishPendingMessages() {
        // 1. 抢占本实例本批次可发布的消息
        List<DocumentPipelineOutbox> messages = outboxService.claimPublishableMessages(lockOwner, LocalDateTime.now());

        // 2. 逐条发布，单条失败不影响同批次其他消息
        for (DocumentPipelineOutbox outbox : messages) {
            publishSingle(outbox);
        }
    }

    private void publishSingle(DocumentPipelineOutbox outbox) {
        try {
            DocumentPipelineMessage message = objectMapper.readValue(
                    outbox.getMessageBody(), DocumentPipelineMessage.class);
            var result = messagePublisher.publish(message);
            outboxService.markPublished(outbox.getOutboxId());
            log.info("文档流水线Outbox发布成功，outboxId={}，documentId={}，processId={}，messageId={}",
                    outbox.getOutboxId(), outbox.getDocumentId(), outbox.getProcessId(), result.messageId());
        } catch (Exception exception) {
            log.error("文档流水线Outbox发布失败，outboxId={}，documentId={}，processId={}",
                    outbox.getOutboxId(), outbox.getDocumentId(), outbox.getProcessId(), exception);
            try {
                // 1. 发布失败后释放抢占状态，状态更新异常不得中断同批次其他消息
                outboxService.markPublishFailed(outbox.getOutboxId(), exception.getMessage());
            } catch (RuntimeException statusException) {
                log.error("记录Outbox发布失败状态异常，outboxId={}，documentId={}，processId={}",
                        outbox.getOutboxId(), outbox.getDocumentId(), outbox.getProcessId(), statusException);
            }
        }
    }
}
