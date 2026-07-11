package com.nexarag.document.service.impl;

import com.nexarag.document.entity.Document;
import com.nexarag.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档处理失败事务服务，确保失败状态和重试信息独立提交。
 */
@Service
@RequiredArgsConstructor
public class DocumentProcessFailureService {

    private final DocumentService documentService;

    /**
     * 使用独立事务记录文档处理失败信息。
     *
     * @param documentId   文档ID
     * @param failureStage 失败阶段
     * @param reason       失败原因
     * @param detail       失败详情
     * @return 失败处理后的文档
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Document recordFailure(Long documentId, String failureStage, String reason, String detail) {
        // 1. 独立提交失败状态和自动重试信息
        return documentService.recordProcessFailure(documentId, failureStage, reason, detail);
    }
}
