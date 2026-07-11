package com.nexarag.infra.messaging.document.model;

import java.time.LocalDateTime;

/**
 * 文档流水线消息，描述需要进入异步处理流水线的文档及其处理批次。
 *
 * @param documentId 文档ID
 * @param processId 处理批次ID
 * @param schemaVersion 消息结构版本
 * @param createdTime 消息创建时间
 */
public record DocumentPipelineMessage(
        Long documentId,
        String processId,
        Integer schemaVersion,
        LocalDateTime createdTime) {
}
