package com.nexarag.document.vo;

import com.nexarag.document.enums.DocumentStatus;

/**
 * 文档处理状态响应。
 *
 * @param documentId    文档ID
 * @param status        文档状态
 * @param retryCount    已重试次数
 * @param failureStage  失败阶段
 * @param failureReason 失败原因
 */
public record DocumentProcessStatusVO(Long documentId, DocumentStatus status, Integer retryCount,
                                      String failureStage, String failureReason) {
}
