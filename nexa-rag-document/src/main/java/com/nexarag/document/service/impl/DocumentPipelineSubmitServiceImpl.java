package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.converter.DocumentConverter;
import com.nexarag.document.dto.CreateDocumentRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.outbox.entity.DocumentPipelineOutbox;
import com.nexarag.document.outbox.enums.OutboxPublishStatus;
import com.nexarag.document.outbox.service.DocumentPipelineOutboxService;
import com.nexarag.document.service.DocumentPipelineSubmitService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.vo.DocumentProcessStatusVO;
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

    private static final int MESSAGE_SCHEMA_VERSION = 1;

    private final DocumentService documentService;
    private final DocumentPipelineOutboxService outboxService;
    private final DocumentPipelineMessagingProperties messagingProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Document createAndSubmit(CreateDocumentRequest createRequest, ProcessDocumentRequest processRequest) {
        // 1. 创建文档记录
        Document document = documentService.createDocument(createRequest);

        // 2. 生成处理批次并将文档推进到排队状态
        return submit(document.getDocumentId(), processRequest, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentProcessStatusVO submitProcess(Long documentId, ProcessDocumentRequest request) {
        return DocumentConverter.toProcessStatusVO(submit(documentId, request, false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentProcessStatusVO retryProcess(Long documentId) {
        return DocumentConverter.toProcessStatusVO(submit(documentId, null, true));
    }

    private Document submit(Long documentId, ProcessDocumentRequest request, boolean retry) {
        String processId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime createdTime = LocalDateTime.now();

        // 1. 使用新的处理批次推进文档状态，并重置消息消费信息
        Document document = retry
                ? documentService.retryProcess(documentId, processId)
                : documentService.submitProcess(documentId, request, processId);

        // 2. 在同一事务内写入待发布Outbox消息
        DocumentPipelineMessage message = new DocumentPipelineMessage(
                documentId, processId, MESSAGE_SCHEMA_VERSION, createdTime);
        boolean saved = outboxService.save(DocumentPipelineOutbox.builder()
                .outboxId(IdWorker.getId())
                .documentId(documentId)
                .processId(processId)
                .messageKey(documentId + ":" + processId)
                .topic(messagingProperties.getTopic())
                .messageBody(serializeMessage(message))
                .publishStatus(OutboxPublishStatus.PENDING)
                .publishRetryCount(0)
                .nextRetryTime(createdTime)
                .build());
        if (!saved) {
            throw new ServiceException("保存文档流水线Outbox消息失败，documentId=" + documentId);
        }
        return document;
    }

    private String serializeMessage(DocumentPipelineMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("序列化文档流水线消息失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
