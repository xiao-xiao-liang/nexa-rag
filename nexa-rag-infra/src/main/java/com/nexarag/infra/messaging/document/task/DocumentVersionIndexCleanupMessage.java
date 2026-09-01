package com.nexarag.infra.messaging.document.task;

import java.time.LocalDateTime;

/**
 * 文档版本外部索引清理消息，只清理指定文档版本的派生索引。
 *
 * @param outboxId Outbox任务ID
 * @param documentId 文档ID
 * @param documentVersionId 文档版本ID
 * @param operationId 清理操作ID
 * @param taskType 任务类型
 * @param schemaVersion 消息结构版本
 * @param createdTime 创建时间
 */
public record DocumentVersionIndexCleanupMessage(Long outboxId, Long documentId, Long documentVersionId,
                                                 String operationId, String taskType, Integer schemaVersion,
                                                 LocalDateTime createdTime) {
}
