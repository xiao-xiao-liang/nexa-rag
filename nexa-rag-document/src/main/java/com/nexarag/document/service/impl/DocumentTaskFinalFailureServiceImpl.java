package com.nexarag.document.service.impl;

import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentTaskAlertService;
import com.nexarag.document.service.DocumentTaskFinalFailureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档异步任务最终失败处理服务实现，避免父任务已失败但告警任务未创建。
 */
@Service
@RequiredArgsConstructor
public class DocumentTaskFinalFailureServiceImpl implements DocumentTaskFinalFailureService {

    private final DocumentPipelineOutboxService outboxService;
    private final DocumentTaskAlertService taskAlertService;

    /**
     * 在同一事务中完成失败状态写入和告警任务创建。
     *
     * @param outboxId          父任务Outbox ID
     * @param consumeRetryCount 消费重试次数
     * @param failureReason     失败原因
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailedAndCreateAlerts(Long outboxId, int consumeRetryCount, String failureReason) {
        // 1. 仅首次进入FAILED的任务需要创建告警，避免死信重复投递产生重复告警
        boolean markedFailed = outboxService.markTaskFailed(outboxId, consumeRetryCount, failureReason);
        if (!markedFailed) {
            return;
        }

        // 2. 与失败状态在同一事务中创建渠道告警，任一失败均回滚父任务状态
        taskAlertService.createFailureAlerts(outboxId, consumeRetryCount, failureReason);
    }
}
