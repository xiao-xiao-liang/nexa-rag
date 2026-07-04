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
 * @param queuePosition 当前队列位置
 * @param waitingCount  等待处理数量
 * @param running       是否运行中
 * @param workerId      当前 Worker ID
 * @param leaseTtlSeconds 租约剩余秒数
 */
public record DocumentProcessStatusVO(Long documentId, DocumentStatus status, Integer retryCount,
                                      String failureStage, String failureReason, Integer queuePosition,
                                      Integer waitingCount, Boolean running, String workerId,
                                      Long leaseTtlSeconds) {

    /**
     * 创建仅包含文档稳定处理状态的响应，兼容已有处理接口。
     *
     * @param documentId    文档ID
     * @param status        文档状态
     * @param retryCount    已重试次数
     * @param failureStage  失败阶段
     * @param failureReason 失败原因
     */
    public DocumentProcessStatusVO(Long documentId, DocumentStatus status, Integer retryCount,
                                   String failureStage, String failureReason) {
        this(documentId, status, retryCount, failureStage, failureReason, null, null, false, null, null);
    }
}
