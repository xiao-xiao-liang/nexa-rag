package com.nexarag.document.model.vo;

import com.nexarag.document.enums.DocumentTaskStatus;

/**
 * 文档删除后的异步索引清理响应。
 *
 * @param documentId      文档ID
 * @param deleted         是否完成逻辑删除
 * @param cleanupOutboxId 索引清理任务ID
 * @param cleanupStatus   索引清理初始状态
 */
public record DocumentDeleteVO(Long documentId, boolean deleted, Long cleanupOutboxId,
                               DocumentTaskStatus cleanupStatus) {
}
