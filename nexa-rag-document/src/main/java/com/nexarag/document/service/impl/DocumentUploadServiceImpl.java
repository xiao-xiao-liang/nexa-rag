package com.nexarag.document.service.impl;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.config.DocumentUploadRetryProperties;
import com.nexarag.document.model.dto.CreateDocumentRequest;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.dto.UploadDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.service.DocumentPipelineSubmitService;
import com.nexarag.document.service.DocumentUploadRetryWaiter;
import com.nexarag.document.service.DocumentUploadService;
import com.nexarag.document.service.ProcessConfigDefaults;
import com.nexarag.document.model.vo.UploadDocumentResponse;
import com.nexarag.infra.storage.StoredFile;
import com.nexarag.infra.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文档上传服务实现，负责上传原始文件并通过事务提交服务创建文档处理任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadServiceImpl implements DocumentUploadService {

    private final FileStorageService fileStorageService;
    private final DocumentPipelineSubmitService pipelineSubmitService;
    private final ProcessConfigDefaults processConfigDefaults;
    private final MultipartProperties multipartProperties;
    private final DocumentUploadRetryProperties retryProperties;
    private final DocumentUploadRetryWaiter retryWaiter;

    @Override
    public UploadDocumentResponse upload(Long knowledgeBaseId, MultipartFile file, UploadDocumentRequest request) {
        // 1. 校验上传文件，避免无效文件进入对象存储
        validateFile(file);
        UploadDocumentRequest safeRequest = request == null
                ? new UploadDocumentRequest(null, null, null, null, null)
                : request;
        String originalFileName = file.getOriginalFilename();

        // 2. 使用短退避策略保存原始文件
        StoredFile storedFile = saveOriginalFileWithRetry(file, originalFileName);

        // 3. 在同一事务内创建文档、推进排队状态并写入Outbox
        try {
            ProcessDocumentRequest processRequest = processConfigDefaults.merge(
                    FileType.fromFileName(originalFileName), safeRequest);
            Document document = pipelineSubmitService.createAndSubmit(knowledgeBaseId,
                    buildCreateDocumentRequest(safeRequest, originalFileName, storedFile), processRequest);
            log.info("文档上传并提交处理成功，documentId={}，processId={}，status={}",
                    document.getDocumentId(), document.getProcessId(), document.getStatus());
            return new UploadDocumentResponse(document.getDocumentId(), document.getProcessId(), document.getStatus());
        } catch (RuntimeException exception) {
            compensateStoredFile(storedFile.objectName(), exception);
            throw exception;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ClientException("上传文件不能为空", DocumentErrorCode.DOCUMENT_UPLOAD_FILE_INVALID);
        }
        if (!StringUtils.hasText(file.getOriginalFilename())) {
            throw new ClientException("上传文件名不能为空", DocumentErrorCode.DOCUMENT_UPLOAD_FILE_INVALID);
        }

        // 1. 复用Spring Multipart配置限制单文件大小
        if (file.getSize() > multipartProperties.getMaxFileSize().toBytes()) {
            throw new ClientException("上传文件大小超过限制，最大允许" + multipartProperties.getMaxFileSize(),
                    DocumentErrorCode.DOCUMENT_UPLOAD_FILE_INVALID);
        }

        // 2. 在写入对象存储前校验文档类型
        if (FileType.fromFileName(file.getOriginalFilename()) == FileType.UNKNOWN) {
            throw new ClientException("不支持的文档类型，fileName=" + file.getOriginalFilename(),
                    DocumentErrorCode.DOCUMENT_FILE_TYPE_UNSUPPORTED);
        }
    }

    private StoredFile saveOriginalFileWithRetry(MultipartFile file, String originalFileName) {
        List<Long> backoffMillis = retryProperties.getBackoffMillis();
        RuntimeException lastException = null;
        for (int attempt = 0; attempt < backoffMillis.size(); attempt++) {
            try {
                return fileStorageService.save(originalFileName, file.getInputStream(), file.getSize());
            } catch (IOException exception) {
                throw new ServiceException("读取上传文件流失败，fileName=" + originalFileName,
                        exception, BaseErrorCode.SERVICE_ERROR);
            } catch (RuntimeException exception) {
                lastException = exception;
                log.warn("保存原始文件失败，准备重试，fileName={}，attempt={}，maxAttempts={}",
                        originalFileName, attempt + 1, backoffMillis.size(), exception);
                if (attempt + 1 < backoffMillis.size()) {
                    retryWaiter.await(backoffMillis.get(attempt));
                }
            }
        }
        throw new ServiceException("保存原始文件重试耗尽，fileName=" + originalFileName,
                lastException, BaseErrorCode.SERVICE_ERROR);
    }

    private CreateDocumentRequest buildCreateDocumentRequest(UploadDocumentRequest request,
                                                             String originalFileName,
                                                             StoredFile storedFile) {
        String title = StringUtils.hasText(request.title()) ? request.title() : originalFileName;
        return new CreateDocumentRequest(title, request.description(), originalFileName,
                storedFile.objectName(), storedFile.url(), storedFile.size());
    }

    private void compensateStoredFile(String objectName, RuntimeException originalException) {
        try {
            // 1. 删除数据库事务失败前已保存的原始对象
            fileStorageService.delete(objectName);
        } catch (RuntimeException cleanupException) {
            log.error("文档提交事务失败后删除原始对象失败，objectName={}", objectName, cleanupException);
            originalException.addSuppressed(cleanupException);
        }
    }
}
