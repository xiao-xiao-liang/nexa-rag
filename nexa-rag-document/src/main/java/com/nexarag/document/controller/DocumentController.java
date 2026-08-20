package com.nexarag.document.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.common.web.PageVO;
import com.nexarag.document.converter.DocumentConverter;
import com.nexarag.document.model.dto.CreateDocumentRequest;
import com.nexarag.document.model.dto.ExternalDocumentSubmitDTO;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.dto.UploadDocumentRequest;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentPipelineSubmitService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentUploadService;
import com.nexarag.document.service.KnowledgeBaseService;
import com.nexarag.document.service.impl.ExternalDocumentSubmitServiceImpl;
import com.nexarag.document.model.vo.DocumentChunkVO;
import com.nexarag.document.model.vo.DocumentDetailVO;
import com.nexarag.document.model.vo.DocumentDeleteVO;
import com.nexarag.document.model.vo.DocumentOverviewVO;
import com.nexarag.document.model.vo.DocumentProcessStatusVO;
import com.nexarag.document.model.vo.DocumentSummaryVO;
import com.nexarag.document.model.vo.UploadDocumentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档接口控制器，负责接收文档相关 REST 请求并返回统一响应。
 */
@RestController
@RequestMapping("/api/knowledge-bases/{knowledgeBaseId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentChunkService documentChunkService;
    private final DocumentUploadService documentUploadService;
    private final DocumentPipelineSubmitService documentPipelineSubmitService;
    private final ExternalDocumentSubmitServiceImpl externalDocumentSubmitService;
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建文档记录。
     *
     * @param request 创建文档请求
     * @return 文档详情响应
     */
    @PostMapping
    public Result<DocumentDetailVO> createDocument(@PathVariable Long knowledgeBaseId,
                                                    @Valid @RequestBody CreateDocumentRequest request) {
        knowledgeBaseService.getRequiredKnowledgeBase(knowledgeBaseId);
        return Results.success(DocumentConverter.toDetailVO(documentService.createDocument(knowledgeBaseId, request)));
    }

    /**
     * 上传文档并自动提交处理流水线。
     *
     * @param file    上传文件
     * @param request 上传文档请求
     * @return 上传文档响应
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadDocumentResponse> uploadDocument(@PathVariable Long knowledgeBaseId,
                                                         @RequestPart("file") MultipartFile file,
                                                         @Valid @RequestPart(value = "request", required = false)
                                                         UploadDocumentRequest request) {
        knowledgeBaseService.getRequiredKnowledgeBase(knowledgeBaseId);
        return Results.success(documentUploadService.upload(knowledgeBaseId, file, request));
    }

    /** 受理飞书或语雀单篇文档，并异步提交处理。 */
    @PostMapping("/external")
    public Result<UploadDocumentResponse> submitExternalDocument(@PathVariable Long knowledgeBaseId,
                                                                  @Valid @RequestBody ExternalDocumentSubmitDTO request) {
        knowledgeBaseService.getRequiredKnowledgeBase(knowledgeBaseId);
        return Results.success(externalDocumentSubmitService.submit(knowledgeBaseId, request));
    }

    /**
     * 分页查询文档列表。
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 文档分页列表
     */
    @GetMapping
    public Result<PageVO<DocumentSummaryVO>> listDocuments(@PathVariable Long knowledgeBaseId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        knowledgeBaseService.getRequiredKnowledgeBase(knowledgeBaseId);
        return Results.success(documentService.pageDocuments(knowledgeBaseId, pageNum, pageSize));
    }

    /**
     * 查询文档详情。
     *
     * @param documentId 文档ID
     * @return 文档详情响应
     */
    @GetMapping("/{documentId}")
    public Result<DocumentDetailVO> getDocument(@PathVariable Long knowledgeBaseId, @PathVariable Long documentId) {
        return Results.success(DocumentConverter.toDetailVO(
                knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId)));
    }

    /**
     * 查询文档诊断概览，包含基础信息、处理配置快照与片段状态统计。
     *
     * @param documentId 文档ID
     * @return 文档诊断概览
     */
    @GetMapping("/{documentId}/overview")
    public Result<DocumentOverviewVO> getDocumentOverview(@PathVariable Long knowledgeBaseId, @PathVariable Long documentId) {
        knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId);
        return Results.success(documentService.getOverview(documentId));
    }

    /**
     * 删除文档。
     *
     * @param documentId 文档ID
     * @return 删除结果
     */
    @DeleteMapping("/{documentId}")
    public Result<DocumentDeleteVO> deleteDocument(@PathVariable Long knowledgeBaseId, @PathVariable Long documentId) {
        knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId);
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
    public Result<DocumentProcessStatusVO> processDocument(@PathVariable Long knowledgeBaseId, @PathVariable Long documentId,
                                                           @Valid @RequestBody(required = false) ProcessDocumentRequest request) {
        knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId);
        return Results.success(documentPipelineSubmitService.submitProcess(documentId, request));
    }

    /**
     * 人工重试失败文档。
     *
     * @param documentId 文档ID
     * @return 文档处理状态响应
     */
    @PostMapping("/{documentId}/retry")
    public Result<DocumentProcessStatusVO> retryDocument(@PathVariable Long knowledgeBaseId, @PathVariable Long documentId) {
        knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId);
        return Results.success(documentPipelineSubmitService.retryProcess(documentId));
    }

    /**
     * 查询文档处理状态。
     *
     * @param documentId 文档ID
     * @return 文档处理状态响应
     */
    @GetMapping("/{documentId}/process-status")
    public Result<DocumentProcessStatusVO> getProcessStatus(@PathVariable Long knowledgeBaseId, @PathVariable Long documentId) {
        return Results.success(DocumentConverter.toProcessStatusVO(
                knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId)));
    }

    /**
     * 查询文档片段。
     *
     * @param documentId 文档ID
     * @param pageNum    页码
     * @param pageSize   每页数量
     * @return 文档片段分页列表
     */
    @GetMapping("/{documentId}/chunks")
    public Result<PageVO<DocumentChunkVO>> listChunks(
            @PathVariable Long knowledgeBaseId,
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId);
        return Results.success(DocumentConverter.toChunkPageVO(
                documentChunkService.pageByDocumentId(documentId, pageNum, pageSize)));
    }
}
