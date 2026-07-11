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

    /**
     * 校验文档流水线消息关键字段。
     */
    public DocumentPipelineMessage {
        // 1. 校验文档ID
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException("文档ID必须大于0");
        }
        // 2. 校验处理批次ID
        if (processId == null || processId.isBlank()) {
            throw new IllegalArgumentException("处理批次ID不能为空");
        }
        // 3. 校验消息结构版本
        if (schemaVersion == null || schemaVersion <= 0) {
            throw new IllegalArgumentException("消息结构版本必须大于0");
        }
        // 4. 校验消息创建时间
        if (createdTime == null) {
            throw new IllegalArgumentException("消息创建时间不能为空");
        }
    }
}
