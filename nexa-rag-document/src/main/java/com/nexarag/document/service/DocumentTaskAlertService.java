package com.nexarag.document.service;

/**
 * 文档任务最终失败告警编排服务，负责创建独立的渠道Outbox任务。
 */
public interface DocumentTaskAlertService {

    /**
     * 为已进入最终失败状态的父任务创建飞书和邮件告警任务。
     *
     * @param parentOutboxId 父任务Outbox ID
     * @param consumeRetryCount 父任务消费次数
     * @param failureReason 脱敏失败原因
     */
    void createFailureAlerts(Long parentOutboxId, int consumeRetryCount, String failureReason);
}
