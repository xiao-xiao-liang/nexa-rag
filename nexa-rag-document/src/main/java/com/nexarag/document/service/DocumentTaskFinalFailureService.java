package com.nexarag.document.service;

/**
 * 文档异步任务最终失败处理服务，保证父任务状态和告警任务在同一事务中写入。
 */
public interface DocumentTaskFinalFailureService {

    /**
     * 标记任务最终失败并创建告警任务。
     *
     * @param outboxId          父任务Outbox ID
     * @param consumeRetryCount 消费重试次数
     * @param failureReason     失败原因
     */
    void markFailedAndCreateAlerts(Long outboxId, int consumeRetryCount, String failureReason);
}
