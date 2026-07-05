package com.nexarag.document.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.dto.ParseConfigRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.service.DocumentChunkingService;
import com.nexarag.document.service.DocumentPipelineExecutor;
import com.nexarag.document.service.DocumentService;
import com.nexarag.infra.parser.DocumentParseRequest;
import com.nexarag.infra.parser.DocumentParseResult;
import com.nexarag.infra.parser.DocumentParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 本地文档流水线执行器，负责在 Workflow Graph 接入前编排解析阶段能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalDocumentPipelineExecutor implements DocumentPipelineExecutor {

    private static final String FAILURE_STAGE_PARSE = "PARSE";
    private static final String FAILURE_REASON_PARSE = "文档解析失败";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocumentService documentService;
    private final DocumentParseService documentParseService;
    private final DocumentChunkingService documentChunkingService;

    /**
     * 执行文档入库流水线的解析阶段。
     *
     * @param documentId 文档ID
     */
    @Override
    public void execute(Long documentId) {
        Document document = documentService.getRequiredDocument(documentId);
        if (document.getStatus() != DocumentStatus.QUEUED) {
            log.warn("文档当前状态不是QUEUED，跳过本次流水线执行，documentId={}，status={}",
                    documentId, document.getStatus());
            return;
        }

        // 1. 推进到解析中状态，避免同一文档被重复解析
        markParsing(document);

        try {
            // 2. 调用 infra parser 完成真实解析并保存解析产物
            DocumentParseResult parseResult = documentParseService.parse(buildParseRequest(document));

            // 3. 回写解析产物并推进到 PARSED
            markParsed(document, parseResult);
            log.info("文档解析阶段执行完成，documentId={}，parsedObjectName={}",
                    documentId, parseResult.parsedObjectName());
        } catch (RuntimeException exception) {
            // 4. 记录解析失败，仍需重试时继续抛出给 Worker 释放租约回队
            Document failureDocument = documentService.recordProcessFailure(documentId, FAILURE_STAGE_PARSE,
                    FAILURE_REASON_PARSE, exception.getMessage());
            if (failureDocument.getStatus() == DocumentStatus.QUEUED) {
                throw new ServiceException("文档解析失败，documentId=" + documentId, exception,
                        com.nexarag.document.error.DocumentErrorCode.DOCUMENT_STATUS_INVALID);
            }
            log.error("文档解析失败且不再重试，documentId={}，status={}", documentId, failureDocument.getStatus(), exception);
            return;
        }

        // 5. 初版本地流水线在同一次任务中继续执行切分阶段
        documentChunkingService.chunk(documentId);
    }

    private void markParsing(Document document) {
        document.setStatus(DocumentStatus.PARSING);
        document.setProcessStartTime(java.time.LocalDateTime.now());
        boolean updated = documentService.updateById(document);
        if (!updated) {
            throw new ServiceException("更新文档解析中状态失败，documentId=" + document.getDocumentId());
        }
    }

    private DocumentParseRequest buildParseRequest(Document document) {
        ParseConfigRequest parseConfig = readParseConfig(document.getProcessConfigJson());
        return DocumentParseRequest.builder()
                .documentId(document.getDocumentId())
                .originalFileName(document.getOriginalFileName())
                .fileType(document.getFileType().name())
                .originalObjectName(document.getOriginalObjectName())
                .originalFileUrl(document.getOriginalFileUrl())
                .enableOcr(parseConfig == null ? null : parseConfig.enableOcr())
                .enableImageDescription(parseConfig == null ? null : parseConfig.enableImageDescription())
                .build();
    }

    private ParseConfigRequest readParseConfig(String processConfigJson) {
        if (!StringUtils.hasText(processConfigJson)) {
            return null;
        }
        try {
            ProcessDocumentRequest request = OBJECT_MAPPER.readValue(processConfigJson, ProcessDocumentRequest.class);
            return request.parseConfig();
        } catch (JsonProcessingException exception) {
            throw new ServiceException("读取文档解析配置失败", exception,
                    com.nexarag.document.error.DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }

    private void markParsed(Document document, DocumentParseResult parseResult) {
        document.setParsedFileUrl(parseResult.parsedFileUrl());
        document.setParsedObjectName(parseResult.parsedObjectName());
        document.setParsedContentType(parseResult.contentType());
        document.setFailureStage(null);
        document.setFailureReason(null);
        document.setFailureDetail(null);
        document.setStatus(DocumentStatus.PARSED);
        boolean updated = documentService.updateById(document);
        if (!updated) {
            throw new ServiceException("更新文档解析完成状态失败，documentId=" + document.getDocumentId());
        }
    }
}
