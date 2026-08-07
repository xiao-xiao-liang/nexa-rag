package com.nexarag.infra.messaging.document.task;

import java.time.LocalDateTime;

/**
 * 索引清理和告警任务使用的无敏感信息消息体。
 *
 * @param outboxId       当前任务Outbox ID
 * @param documentId     文档ID
 * @param parentOutboxId 父任务Outbox ID
 * @param operationId    任务操作版本ID
 * @param taskType       任务类型
 * @param schemaVersion  消息结构版本
 * @param createdTime    创建时间
 */
public record DocumentTaskMessage(Long outboxId, Long documentId, Long parentOutboxId,
                                  String operationId, String taskType, Integer schemaVersion,
                                  LocalDateTime createdTime) {
}
