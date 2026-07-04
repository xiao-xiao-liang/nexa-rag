package com.nexarag.document.vo;

import com.nexarag.document.enums.DocumentStatus;

/**
 * 上传文档响应，返回文档稳定状态和实时排队概况。
 *
 * @param documentId    文档ID
 * @param status        文档状态
 * @param queuePosition 当前队列位置
 * @param waitingCount  等待处理数量
 */
public record UploadDocumentResponse(Long documentId,
                                     DocumentStatus status,
                                     Integer queuePosition,
                                     Integer waitingCount) {
}
