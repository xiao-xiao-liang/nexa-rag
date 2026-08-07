package com.nexarag.infra.alert.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 跨模块投递的脱敏告警消息。
 *
 * @param outboxId 当前告警任务Outbox ID
 * @param documentId 关联文档ID
 * @param parentOutboxId 父任务Outbox ID
 * @param operationId 任务操作版本ID
 * @param taskType 父任务类型
 * @param severity 告警严重级别
 * @param channel 投递渠道
 * @param failureReason 脱敏后的失败原因
 * @param consumeRetryCount 父任务已消费次数
 * @param failureTime 父任务最终失败时间
 */
public record AlertMessage(Long outboxId, Long documentId, Long parentOutboxId,
                           String operationId, String taskType, AlertSeverity severity,
                           AlertChannel channel, String failureReason, Integer consumeRetryCount,
                           LocalDateTime failureTime) {

    private static final int MAX_FAILURE_REASON_LENGTH = 1024;

    /**
     * 校验消息关键字段并标准化失败原因。
     */
    public AlertMessage {
        // 1. 校验任务关联信息，确保生命周期回调可准确更新业务任务
        requirePositiveId(outboxId, "告警任务Outbox ID");
        requirePositiveId(documentId, "文档ID");
        requirePositiveId(parentOutboxId, "父任务Outbox ID");
        requireText(operationId, "任务操作版本ID");
        requireText(taskType, "父任务类型");
        Objects.requireNonNull(severity, "告警严重级别不能为空");
        Objects.requireNonNull(channel, "告警渠道不能为空");
        if (consumeRetryCount == null || consumeRetryCount < 1) {
            throw new IllegalArgumentException("消费次数必须大于0");
        }
        Objects.requireNonNull(failureTime, "失败时间不能为空");

        // 2. 在消息边界再次清理敏感内容，防止跨模块透传原始异常详情
        failureReason = sanitizeFailureReason(failureReason);
    }

    private static void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "必须大于0");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
    }

    private static String sanitizeFailureReason(String rawFailureReason) {
        if (rawFailureReason == null || rawFailureReason.isBlank()) {
            return "未知失败原因";
        }
        String sanitized = rawFailureReason
                .replaceAll("(?i)bearer\\s+\\S+", "[凭据已脱敏]")
                .replaceAll("(?i)sk-[a-z0-9_-]+", "[凭据已脱敏]")
                .replaceAll("(?<!\\S)/[^\\s]+", "[路径已脱敏]")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
        if (sanitized.isEmpty()) {
            return "未知失败原因";
        }
        return sanitized.length() <= MAX_FAILURE_REASON_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
