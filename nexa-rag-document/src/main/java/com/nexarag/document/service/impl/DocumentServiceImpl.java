package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.dto.CreateDocumentRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.error.DocumentErrorCode;
import com.nexarag.document.mapper.DocumentMapper;
import com.nexarag.document.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 文档服务实现类，负责文档记录创建、处理提交和重试状态流转。
 */
@Slf4j
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    private static final String QUEUE_STAGE_PIPELINE = "PIPELINE";
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Document createDocument(CreateDocumentRequest request) {
        FileType fileType = FileType.fromFileName(request.originalFileName());
        if (fileType == FileType.UNKNOWN) {
            throw new ClientException("不支持的文档类型，fileName=" + request.originalFileName(),
                    DocumentErrorCode.DOCUMENT_FILE_TYPE_UNSUPPORTED);
        }

        // 1. 构建文档实体
        Document document = Document.builder()
                .documentId(IdWorker.getId())
                .title(request.title())
                .description(request.description())
                .originalFileName(request.originalFileName())
                .originalFileUrl(request.originalFileUrl())
                .fileSize(request.fileSize())
                .fileType(fileType)
                .status(DocumentStatus.UPLOADED)
                .retryCount(0)
                .maxRetryCount(DEFAULT_MAX_RETRY_COUNT)
                .cleanupRetryCount(0)
                .build();

        // 2. 保存文档记录
        this.save(document);
        log.info("创建文档记录成功，documentId={}，fileType={}，status={}",
                document.getDocumentId(), document.getFileType(), document.getStatus());
        return document;
    }

    @Override
    public Document submitProcess(Long documentId, ProcessDocumentRequest request) {
        Document document = getRequiredDocument(documentId);
        DocumentStatus oldStatus = document.getStatus();
        if (!oldStatus.canTransferTo(DocumentStatus.QUEUED)) {
            throw new ClientException("文档状态不允许提交处理，documentId=" + documentId + "，status=" + oldStatus,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }

        // 1. 保存处理配置快照
        document.setProcessConfigJson(serializeProcessConfig(request));

        // 2. 更新排队状态
        document.setStatus(DocumentStatus.QUEUED);
        document.setQueueStage(QUEUE_STAGE_PIPELINE);
        document.setQueueTime(LocalDateTime.now());
        this.updateById(document);
        log.info("文档提交处理成功，documentId={}，oldStatus={}，newStatus={}",
                documentId, oldStatus, document.getStatus());
        return document;
    }

    @Override
    public Document retryProcess(Long documentId) {
        Document document = getRequiredDocument(documentId);
        DocumentStatus oldStatus = document.getStatus();
        if (oldStatus != DocumentStatus.FAILED) {
            throw new ClientException("只有失败文档允许重试，documentId=" + documentId + "，status=" + oldStatus,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }

        // 1. 增加重试次数
        document.setRetryCount(document.getRetryCount() == null ? 1 : document.getRetryCount() + 1);
        document.setLastRetryTime(LocalDateTime.now());

        // 2. 重新进入排队状态
        document.setStatus(DocumentStatus.QUEUED);
        document.setQueueStage(QUEUE_STAGE_PIPELINE);
        document.setQueueTime(LocalDateTime.now());
        this.updateById(document);
        log.warn("文档重新提交处理，documentId={}，oldStatus={}，newStatus={}，retryCount={}",
                documentId, oldStatus, document.getStatus(), document.getRetryCount());
        return document;
    }

    @Override
    public Document getRequiredDocument(Long documentId) {
        Document document = this.getById(documentId);
        if (document == null) {
            throw new ClientException("文档不存在，documentId=" + documentId, DocumentErrorCode.DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    private String serializeProcessConfig(ProcessDocumentRequest request) {
        if (request == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("序列化文档处理配置失败", exception,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }
}
