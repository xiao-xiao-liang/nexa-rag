package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.*;
import com.nexarag.document.model.dto.CreateDocumentRequest;
import com.nexarag.document.model.dto.DocumentVersionUploadDTO;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentTaskOutboxDO;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.model.vo.DocumentProcessStatusVO;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentPipelineSubmitService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.document.constants.DocumentConstants;
import com.nexarag.document.constants.DocumentMessagingConstants;
import com.nexarag.infra.config.DocumentPipelineMessagingProperties;
import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文档流水线提交服务实现，保证文档状态变更与Outbox消息写入的原子性。
 */
@Service
@RequiredArgsConstructor
public class DocumentPipelineSubmitServiceImpl implements DocumentPipelineSubmitService {

    private final DocumentService documentService;
    private final DocumentVersionService documentVersionService;
    private final DocumentPipelineOutboxService outboxService;
    private final DocumentPipelineMessagingProperties messagingProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersionDO createAndSubmit(Long knowledgeBaseId, CreateDocumentRequest createRequest,
                                              ProcessDocumentRequest processRequest, String operator) {
        // 1. 创建稳定文档身份，不向 document 写入文件和处理生命周期字段。
        Document document = documentService.createDocument(knowledgeBaseId, createRequest);

        // 2. 首个文件快照、处理配置和 Outbox 均由 V1 承担。
        return queueNewVersion(document, toVersionUpload(createRequest), processRequest, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersionDO createVersionAndSubmit(Long documentId, DocumentVersionUploadDTO upload,
                                                     ProcessDocumentRequest processRequest, String operator) {
        Document document = documentService.getRequiredDocument(documentId);
        return queueNewVersion(document, upload, processRequest, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersionDO retryVersion(Long documentId, Long documentVersionId, String operator) {
        String processId = UUID.randomUUID().toString().replace("-", "");
        DocumentVersionDO documentVersion = documentVersionService.retryFailedVersion(documentId, documentVersionId,
                processId, operator);
        saveOutbox(documentId, documentVersionId, processId, LocalDateTime.now());
        return documentVersion;
    }

    private DocumentVersionDO queueNewVersion(Document document, DocumentVersionUploadDTO upload,
                                              ProcessDocumentRequest processRequest, String operator) {
        String processId = UUID.randomUUID().toString().replace("-", "");
        DocumentVersionDO documentVersion = documentVersionService.createNextVersion(document.getDocumentId(),
                upload, processId, operator);
        documentVersion.setProcessId(processId);
        documentVersion.setStatus(DocumentVersionStatus.QUEUED);
        documentVersion.setQueueStage(DocumentConstants.QUEUE_STAGE_PIPELINE);
        documentVersion.setQueueTime(LocalDateTime.now());
        documentVersion.setUpdateBy(operator);
        documentVersion.setProcessConfigJson(serializeRequest(processRequest));
        if (!documentVersionService.updateById(documentVersion)) {
            throw new ServiceException("更新文档版本排队状态失败，documentId=" + document.getDocumentId()
                    + "，documentVersionId=" + documentVersion.getDocumentVersionId());
        }
        saveOutbox(document.getDocumentId(), documentVersion.getDocumentVersionId(), processId, LocalDateTime.now());
        return documentVersion;
    }

    @Override
    public DocumentProcessStatusVO submitProcess(Long documentId, ProcessDocumentRequest request) {
        throw legacyProcessOperationException(documentId);
    }

    @Override
    public DocumentProcessStatusVO retryProcess(Long documentId) {
        throw legacyProcessOperationException(documentId);
    }

    private ClientException legacyProcessOperationException(Long documentId) {
        return new ClientException("文档处理必须通过文档版本接口发起，documentId=" + documentId,
                com.nexarag.document.enums.DocumentErrorCode.DOCUMENT_STATUS_INVALID);
    }

    private void saveOutbox(Long documentId, Long documentVersionId, String processId, LocalDateTime createdTime) {
        if (documentVersionId == null || documentVersionId <= 0) {
            throw new ServiceException("保存文档流水线Outbox消息失败，缺少文档版本ID，documentId=" + documentId);
        }
        Long outboxId = IdWorker.getId();
        DocumentPipelineMessage message = new DocumentPipelineMessage(
                documentId, documentVersionId, processId, outboxId,
                DocumentMessagingConstants.MESSAGE_SCHEMA_VERSION, createdTime);
        boolean saved = outboxService.save(DocumentTaskOutboxDO.builder()
                .outboxId(outboxId)
                .documentId(documentId)
                .documentVersionId(documentVersionId)
                .processId(processId)
                .taskType(DocumentTaskType.PROCESS_DOCUMENT)
                .messageKey(documentId + ":" + documentVersionId + ":" + DocumentTaskType.PROCESS_DOCUMENT
                        + ":" + processId)
                .topic(messagingProperties.getTopic())
                .messageBody(serializeMessage(message))
                .publishStatus(OutboxPublishStatus.PENDING)
                .taskStatus(DocumentTaskStatus.PENDING)
                .publishRetryCount(0)
                .consumeRetryCount(0)
                .nextRetryTime(createdTime)
                .build());
        if (!saved) {
            throw new ServiceException("保存文档流水线Outbox消息失败，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId);
        }
    }

    private DocumentVersionUploadDTO toVersionUpload(CreateDocumentRequest request) {
        return DocumentVersionUploadDTO.builder()
                .originalFileName(request.originalFileName())
                .fileType(FileType.fromFileName(request.originalFileName()))
                .fileSize(request.fileSize())
                .originalFileUrl(request.originalFileUrl())
                .originalObjectName(request.originalObjectName())
                .sourceType(request.sourceType())
                .sourceUrl(request.sourceUrl())
                .build();
    }

    private String serializeRequest(ProcessDocumentRequest request) {
        try {
            return objectMapper.writeValueAsString(request == null ? new ProcessDocumentRequest(null, null, null) : request);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("序列化文档版本处理配置失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private String serializeMessage(DocumentPipelineMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("序列化文档流水线消息失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
