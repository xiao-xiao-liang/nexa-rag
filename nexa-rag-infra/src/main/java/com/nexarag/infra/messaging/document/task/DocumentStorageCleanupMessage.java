package com.nexarag.infra.messaging.document.task;

import java.time.LocalDateTime;

/**
 * 文档对象存储清理消息，保存删除时已确定的对象名，避免逻辑删除后无法读取文档记录。
 *
 * @param outboxId           Outbox任务ID
 * @param documentId         文档ID
 * @param operationId        清理操作ID
 * @param taskType           任务类型
 * @param schemaVersion      消息结构版本
 * @param originalObjectName 原始文件对象名
 * @param parsedObjectName   解析文件对象名
 * @param createdTime        创建时间
 */
public record DocumentStorageCleanupMessage(Long outboxId, Long documentId, String operationId, String taskType,
                                            Integer schemaVersion, String originalObjectName, String parsedObjectName,
                                            LocalDateTime createdTime) {
}
