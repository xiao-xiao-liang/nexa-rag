package com.nexarag.infra.messaging.document.task;

import java.time.LocalDateTime;

/**
 * 文档版本对象存储清理消息，仅删除该版本已固化的对象，不允许按文档目录前缀删除。
 *
 * @param outboxId Outbox任务ID
 * @param documentId 文档ID
 * @param documentVersionId 文档版本ID
 * @param operationId 清理操作ID
 * @param taskType 任务类型
 * @param schemaVersion 消息结构版本
 * @param originalObjectName 原始对象名
 * @param parsedObjectName 解析对象名
 * @param createdTime 创建时间
 */
public record DocumentVersionStorageCleanupMessage(Long outboxId, Long documentId, Long documentVersionId,
                                                   String operationId, String taskType, Integer schemaVersion,
                                                   String originalObjectName, String parsedObjectName,
                                                   LocalDateTime createdTime) {
}
