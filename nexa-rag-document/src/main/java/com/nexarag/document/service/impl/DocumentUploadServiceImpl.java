package com.nexarag.document.service.impl;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.dto.CreateDocumentRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.dto.UploadDocumentRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.error.DocumentErrorCode;
import com.nexarag.document.service.DocumentProcessTaskDispatcher;
import com.nexarag.document.service.DocumentQueueInfo;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentUploadService;
import com.nexarag.document.service.ProcessConfigDefaults;
import com.nexarag.document.vo.UploadDocumentResponse;
import com.nexarag.infra.storage.FileStorageService;
import com.nexarag.infra.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文档上传服务实现，负责保存原始文件、创建文档记录并投递处理流水线。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadServiceImpl implements DocumentUploadService {

    private final FileStorageService fileStorageService;
    private final DocumentService documentService;
    private final ProcessConfigDefaults processConfigDefaults;
    private final DocumentProcessTaskDispatcher taskDispatcher;

    /**
     * 上传文档并提交处理。
     *
     * @param file    上传文件
     * @param request 上传文档请求
     * @return 上传响应
     */
    @Override
    public UploadDocumentResponse upload(MultipartFile file, UploadDocumentRequest request) {
        // 1. 校验上传文件，避免空文件进入后续流水线
        validateFile(file);
        UploadDocumentRequest safeRequest = request == null
                ? new UploadDocumentRequest(null, null, null, null, null)
                : request;
        String originalFileName = file.getOriginalFilename();

        // 2. 保存原始文件到对象存储
        StoredFile storedFile = saveOriginalFile(file, originalFileName);

        // 3. 创建文档记录，稳定状态先落为 UPLOADED
        Document uploadedDocument = documentService.createDocument(buildCreateDocumentRequest(
                safeRequest, originalFileName, storedFile));

        // 4. 合并默认处理配置并推进为 QUEUED
        ProcessDocumentRequest processRequest = processConfigDefaults.merge(uploadedDocument.getFileType(), safeRequest);
        Document queuedDocument = documentService.submitProcess(uploadedDocument.getDocumentId(), processRequest);

        // 5. 投递处理任务并立即返回排队信息
        DocumentQueueInfo queueInfo = taskDispatcher.enqueue(queuedDocument.getDocumentId());
        log.info("文档上传并提交处理成功，documentId={}，status={}",
                queuedDocument.getDocumentId(), queuedDocument.getStatus());
        return new UploadDocumentResponse(queuedDocument.getDocumentId(), queuedDocument.getStatus(),
                queueInfo.queuePosition(), queueInfo.waitingCount());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ClientException("上传文件不能为空", DocumentErrorCode.DOCUMENT_UPLOAD_FILE_INVALID);
        }
        if (!StringUtils.hasText(file.getOriginalFilename())) {
            throw new ClientException("上传文件名不能为空", DocumentErrorCode.DOCUMENT_UPLOAD_FILE_INVALID);
        }
    }

    private StoredFile saveOriginalFile(MultipartFile file, String originalFileName) {
        try {
            return fileStorageService.save(originalFileName, file.getInputStream(), file.getSize());
        } catch (IOException exception) {
            throw new ServiceException("读取上传文件流失败，fileName=" + originalFileName,
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private CreateDocumentRequest buildCreateDocumentRequest(UploadDocumentRequest request,
                                                             String originalFileName,
                                                             StoredFile storedFile) {
        String title = StringUtils.hasText(request.title()) ? request.title() : originalFileName;
        return new CreateDocumentRequest(title, request.description(), originalFileName, storedFile.url(), storedFile.size());
    }
}
