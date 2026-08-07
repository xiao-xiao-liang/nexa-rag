package com.nexarag.document.service.impl;

import com.nexarag.document.model.entity.Document;
import com.nexarag.infra.messaging.document.model.DocumentPipelineFailureMessage;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentTaskAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档处理失败事务服务，确保失败状态和重试信息独立提交。
 */
@Service
@RequiredArgsConstructor
public class DocumentProcessFailureService {

    private final DocumentService documentService;
    private final DocumentTaskAlertService taskAlertService;

    /**
     * 使用独立事务记录文档处理失败信息。
     *
     * @param documentId   文档ID
     * @param failureStage 失败阶段
     * @param reason       失败原因
     * @param detail       失败详情
     * @return 失败处理后的文档
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Document recordFailure(Long documentId, String failureStage, String reason, String detail) {
        // 1. 独立提交失败状态和自动重试信息
        return documentService.recordProcessFailure(documentId, failureStage, reason, detail);
    }

    /**
     * 使用独立事务标记当前处理轮次最终失败并创建渠道告警任务。
     *
     * @param message 失败消息
     * @return true表示当前轮次已标记失败，false表示消息属于旧轮次
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markFinalFailure(DocumentPipelineFailureMessage message) {
        // 1. 条件更新当前处理轮次为最终失败
        boolean updated = documentService.markProcessFailed(message.documentId(), message.processId(),
                message.failureStage(), message.failureReason(), message.failureDetail(),
                message.consumedTimes(), message.messageId(), message.failureTime());
        if (!updated) {
            return false;
        }

        // 2. 仅在父任务状态更新成功后创建独立的渠道告警任务
        if (message.outboxId() != null) {
            taskAlertService.createFailureAlerts(message.outboxId(), message.consumedTimes(), message.failureReason());
        }
        return true;
    }
}
