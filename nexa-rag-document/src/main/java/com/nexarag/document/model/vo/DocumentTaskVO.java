package com.nexarag.document.model.vo;

import com.nexarag.document.enums.DocumentTaskStatus;
import com.nexarag.document.enums.DocumentTaskType;
import com.nexarag.document.enums.OutboxPublishStatus;

import java.time.LocalDateTime;

/**
 * 文档异步任务展示对象，不包含原始消息体和任何渠道凭据。
 *
 * @param outboxId 任务Outbox ID
 * @param documentId 文档ID
 * @param parentOutboxId 父任务Outbox ID
 * @param operationId 任务操作版本ID
 * @param taskType 任务类型
 * @param publishStatus 发布状态
 * @param taskStatus 消费状态
 * @param publishRetryCount 发布重试次数
 * @param consumeRetryCount 消费重试次数
 * @param failureReason 脱敏失败原因
 * @param completedTime 完成时间
 */
public record DocumentTaskVO(Long outboxId, Long documentId, Long parentOutboxId, String operationId,
                             DocumentTaskType taskType, OutboxPublishStatus publishStatus,
                             DocumentTaskStatus taskStatus, Integer publishRetryCount,
                             Integer consumeRetryCount, String failureReason, LocalDateTime completedTime) {
}
