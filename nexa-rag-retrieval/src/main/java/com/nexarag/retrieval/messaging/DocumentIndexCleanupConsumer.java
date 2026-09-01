package com.nexarag.retrieval.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.constants.DocumentMessagingConstants;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentDeleteTaskService;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.infra.messaging.document.task.DocumentVersionIndexCleanupMessage;
import com.nexarag.retrieval.service.DocumentVersionIndexCleaner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 文档索引清理消费者，仅处理带有文档版本边界的索引清理消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = DocumentMessagingConstants.INDEX_CLEANUP_TOPIC,
        consumerGroup = DocumentMessagingConstants.INDEX_CLEANUP_CONSUMER_GROUP,
        maxReconsumeTimes = DocumentMessagingConstants.MAX_RECONSUME_TIMES)
public class DocumentIndexCleanupConsumer implements RocketMQListener<MessageExt> {

    private final DocumentVersionIndexCleaner documentVersionIndexCleaner;
    private final DocumentPipelineOutboxService outboxService;
    private final DocumentVersionService documentVersionService;
    private final DocumentDeleteTaskService documentDeleteTaskService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt messageExt) {
        cleanupVersion(messageExt);
    }

    private void cleanupVersion(MessageExt messageExt) {
        DocumentVersionIndexCleanupMessage message = deserializeVersion(messageExt);
        if (message.outboxId() == null || message.documentId() == null || message.documentVersionId() == null) {
            throw new ServiceException("拒绝缺少文档版本边界的索引清理消息，messageId=" + messageExt.getMsgId());
        }
        if (!outboxService.markTaskProcessing(message.outboxId(), Math.max(messageExt.getReconsumeTimes() + 1, 1))) {
            return;
        }
        documentVersionIndexCleaner.cleanup(message.documentId(), message.documentVersionId());
        DocumentVersionDO documentVersion = documentVersionService.getRequiredVersion(message.documentId(),
                message.documentVersionId());
        if (documentVersion.getStatus() != DocumentVersionStatus.DELETING) {
            throw new ServiceException("文档版本未处于删除中状态，documentId=" + message.documentId()
                    + "，documentVersionId=" + message.documentVersionId());
        }
        documentDeleteTaskService.createVersionStorageCleanupTask(documentVersion);
        outboxService.markTaskSucceeded(message.outboxId());
        log.info("文档版本外部索引清理完成，outboxId={}，documentId={}，documentVersionId={}，operationId={}",
                message.outboxId(), message.documentId(), message.documentVersionId(), message.operationId());
    }

    private DocumentVersionIndexCleanupMessage deserializeVersion(MessageExt messageExt) {
        try {
            return objectMapper.readValue(messageExt.getBody(), DocumentVersionIndexCleanupMessage.class);
        } catch (Exception exception) {
            throw new ServiceException("解析文档版本索引清理消息失败，messageId=" + messageExt.getMsgId(), exception,
                    BaseErrorCode.SERVICE_ERROR);
        }
    }

}
