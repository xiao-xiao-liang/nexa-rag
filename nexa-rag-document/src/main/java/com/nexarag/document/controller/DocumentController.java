package com.nexarag.document.controller;

import com.nexarag.auth.service.CurrentUserAccountNameProvider;
import com.nexarag.common.web.PageVO;
import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.document.converter.DocumentConverter;
import com.nexarag.document.model.dto.CreateDocumentRequest;
import com.nexarag.document.model.dto.ExternalDocumentSubmitDTO;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.dto.UploadDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.vo.*;
import com.nexarag.document.service.*;
import com.nexarag.document.service.impl.ExternalDocumentSubmitServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档接口控制器，负责接收文档相关 REST 请求并返回统一响应。
 */
@RestController
@RequestMapping("/api/knowledge-bases/{knowledgeBaseId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentUploadService documentUploadService;
    private final DocumentVersionService documentVersionService;
    private final DocumentPipelineSubmitService documentPipelineSubmitService;
    private final ExternalDocumentSubmitServiceImpl externalDocumentSubmitService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final CurrentUserAccountNameProvider currentUserAccountNameProvider;

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
        Document document = documentService.createDocument(knowledgeBaseId, request);
        return Results.success(documentService.getDocumentDetail(document.getDocumentId()));
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
        return Results.success(documentUploadService.upload(knowledgeBaseId, file, request,
                currentUserAccountNameProvider.getCurrentAccountName()));
    }

    /**
     * 上传已有文档的新文件版本；同一文档存在构建中版本时由版本服务拒绝。
     */
    @PostMapping(value = "/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadDocumentResponse> uploadDocumentVersion(@PathVariable Long knowledgeBaseId,
                                                                @PathVariable Long documentId,
                                                                @RequestPart("file") MultipartFile file,
                                                                @Valid @RequestPart(value = "request", required = false)
                                                                UploadDocumentRequest request) {
        knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId);
        return Results.success(documentUploadService.uploadVersion(documentId, file, request,
                currentUserAccountNameProvider.getCurrentAccountName()));
    }

    /**
     * 查询文档版本历史。
     */
    @GetMapping("/{documentId}/versions")
    public Result<PageVO<DocumentVersionVO>> listDocumentVersions(@PathVariable Long knowledgeBaseId,
                                                                  @PathVariable Long documentId,
                                                                  @RequestParam(defaultValue = "1") long pageNum,
                                                                  @RequestParam(defaultValue = "20") long pageSize) {
        knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId);
        return Results.success(documentVersionService.listVersions(documentId, pageNum, pageSize));
    }

    /**
     * 查询指定文档版本详情。
     */
    @GetMapping("/{documentId}/versions/{documentVersionId}")
    public Result<DocumentVersionVO> getDocumentVersion(@PathVariable Long knowledgeBaseId,
                                                        @PathVariable Long documentId,
                                                        @PathVariable Long documentVersionId) {
        knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId);
        return Results.success(documentVersionService.getVersionDetail(documentId, documentVersionId));
    }

    /**
     * 切换至索引预热完成的历史版本。
     */
    @PostMapping("/{documentId}/versions/{documentVersionId}/activate")
    public Result<Void> activateDocumentVersion(@PathVariable Long knowledgeBaseId,
                                                @PathVariable Long documentId,
                                                @PathVariable Long documentVersionId) {
        knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId);
        documentVersionService.activateReadyVersion(documentId, documentVersionId,
                currentUserAccountNameProvider.getCurrentAccountName());
        return Results.success();
    }

    /**
     * 重新提交失败版本。
     */
    @PostMapping("/{documentId}/versions/{documentVersionId}/retry")
    public Result<DocumentVersionVO> retryDocumentVersion(@PathVariable Long knowledgeBaseId,
                                                          @PathVariable Long documentId,
                                                          @PathVariable Long documentVersionId) {
        knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId);
        documentPipelineSubmitService.retryVersion(documentId, documentVersionId,
                currentUserAccountNameProvider.getCurrentAccountName());
        return Results.success(documentVersionService.getVersionDetail(documentId, documentVersionId));
    }

    /**
     * 受理非生效历史版本的永久删除。
     */
    @DeleteMapping("/{documentId}/versions/{documentVersionId}")
    public Result<Void> deleteDocumentVersion(@PathVariable Long knowledgeBaseId,
                                              @PathVariable Long documentId,
                                              @PathVariable Long documentVersionId) {
        knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId);
        documentVersionService.requestPermanentDelete(documentId, documentVersionId,
                currentUserAccountNameProvider.getCurrentAccountName());
        return Results.success();
    }

    /**
     * 查询文档版本操作审计。
     */
    @GetMapping("/{documentId}/version-operation-logs")
    public Result<PageVO<DocumentVersionOperationLogVO>> listDocumentVersionOperationLogs(
            @PathVariable Long knowledgeBaseId, @PathVariable Long documentId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId);
        return Results.success(documentVersionService.listOperationLogs(documentId, pageNum, pageSize));
    }

    /**
     * 受理飞书或语雀单篇文档，并异步提交处理。
     */
    @PostMapping("/external")
    public Result<UploadDocumentResponse> submitExternalDocument(@PathVariable Long knowledgeBaseId,
                                                                 @Valid @RequestBody ExternalDocumentSubmitDTO request) {
        knowledgeBaseService.getRequiredKnowledgeBase(knowledgeBaseId);
        return Results.success(externalDocumentSubmitService.submit(knowledgeBaseId, request,
                currentUserAccountNameProvider.getCurrentAccountName()));
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
        knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId);
        return Results.success(documentService.getDocumentDetail(documentId));
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
        return Results.success(documentService.deleteDocument(documentId,
                currentUserAccountNameProvider.getCurrentAccountName()));
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
        knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId);
        return Results.success(documentService.getProcessStatus(documentId));
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
                documentService.pageActiveVersionChunks(documentId, pageNum, pageSize)));
    }
}
