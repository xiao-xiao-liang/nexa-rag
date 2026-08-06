package com.nexarag.retrieval.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.retrieval.dto.res.DocumentIndexCleanupResult;
import com.nexarag.retrieval.service.DocumentIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档外部索引运维接口，仅支持按单个文档清理，以配合审批后的结构化章节重建。
 */
@RestController
@RequestMapping("/api/document-indexes")
@RequiredArgsConstructor
public class DocumentIndexOperationController {

    private final DocumentIndexService documentIndexService;

    /**
     * 清理指定文档的向量、正文关键词和章节导航外部索引。
     *
     * @param documentId 文档ID
     * @return 各索引清理结果
     */
    @DeleteMapping("/{documentId}")
    public Result<DocumentIndexCleanupResult> cleanupDocumentIndex(@PathVariable Long documentId) {
        return Results.success(documentIndexService.cleanupDocumentIndex(documentId));
    }
}
