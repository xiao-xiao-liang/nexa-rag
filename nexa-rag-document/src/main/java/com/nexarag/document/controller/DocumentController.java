package com.nexarag.document.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.document.converter.DocumentConverter;
import com.nexarag.document.dto.CreateDocumentRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.vo.DocumentChunkVO;
import com.nexarag.document.vo.DocumentDetailVO;
import com.nexarag.document.vo.DocumentProcessStatusVO;
import com.nexarag.document.vo.DocumentSummaryVO;
import com.nexarag.document.vo.PageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档接口控制器，负责接收文档相关 REST 请求并返回统一响应。
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentChunkService documentChunkService;

    /**
     * 创建文档记录。
     *
     * @param request 创建文档请求
     * @return 文档详情响应
     */
    @PostMapping
    public Result<DocumentDetailVO> createDocument(@Valid @RequestBody CreateDocumentRequest request) {
        return Results.success(DocumentConverter.toDetailVO(documentService.createDocument(request)));
    }

    /**
     * 分页查询文档列表。
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 文档分页列表
     */
    @GetMapping
    public Result<PageVO<DocumentSummaryVO>> listDocuments(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Results.success(documentService.pageDocuments(pageNum, pageSize));
    }

    /**
     * 查询文档详情。
     *
     * @param documentId 文档ID
     * @return 文档详情响应
     */
    @GetMapping("/{documentId}")
    public Result<DocumentDetailVO> getDocument(@PathVariable Long documentId) {
        return Results.success(DocumentConverter.toDetailVO(documentService.getRequiredDocument(documentId)));
    }

    /**
     * 删除文档。
     *
     * @param documentId 文档ID
     * @return 删除结果
     */
    @DeleteMapping("/{documentId}")
    public Result<Boolean> deleteDocument(@PathVariable Long documentId) {
        return Results.success(documentService.deleteDocument(documentId));
    }

    /**
     * 提交文档处理。
     *
     * @param documentId 文档ID
     * @param request    文档处理请求
     * @return 文档处理状态响应
     */
    @PostMapping("/{documentId}/process")
    public Result<DocumentProcessStatusVO> processDocument(@PathVariable Long documentId,
                                                           @Valid @RequestBody(required = false) ProcessDocumentRequest request) {
        return Results.success(DocumentConverter.toProcessStatusVO(documentService.submitProcess(documentId, request)));
    }

    /**
     * 人工重试失败文档。
     *
     * @param documentId 文档ID
     * @return 文档处理状态响应
     */
    @PostMapping("/{documentId}/retry")
    public Result<DocumentProcessStatusVO> retryDocument(@PathVariable Long documentId) {
        return Results.success(DocumentConverter.toProcessStatusVO(documentService.retryProcess(documentId)));
    }

    /**
     * 查询文档处理状态。
     *
     * @param documentId 文档ID
     * @return 文档处理状态响应
     */
    @GetMapping("/{documentId}/process-status")
    public Result<DocumentProcessStatusVO> getProcessStatus(@PathVariable Long documentId) {
        return Results.success(DocumentConverter.toProcessStatusVO(documentService.getRequiredDocument(documentId)));
    }

    /**
     * 查询文档片段。
     *
     * @param documentId 文档ID
     * @return 文档片段列表
     */
    @GetMapping("/{documentId}/chunks")
    public Result<List<DocumentChunkVO>> listChunks(@PathVariable Long documentId) {
        return Results.success(documentChunkService.listByDocumentId(documentId).stream()
                .map(DocumentConverter::toChunkVO)
                .toList());
    }
}
