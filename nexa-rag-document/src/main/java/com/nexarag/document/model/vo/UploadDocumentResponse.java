package com.nexarag.document.model.vo;

import com.nexarag.document.enums.DocumentStatus;

/**
 * 上传文档响应，返回文档处理批次和排队状态。
 *
 * @param documentId    文档ID
 * @param processId  处理批次ID
 * @param status     文档状态
 */
public record UploadDocumentResponse(Long documentId,
                                     String processId,
                                     DocumentStatus status) {
}
