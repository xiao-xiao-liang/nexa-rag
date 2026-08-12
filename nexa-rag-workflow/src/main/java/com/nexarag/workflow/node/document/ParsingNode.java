package com.nexarag.workflow.node.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.constants.DocumentConstants;
import com.nexarag.document.model.dto.ParseConfigRequest;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.service.DocumentService;
import com.nexarag.infra.parser.model.DocumentParseRequest;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.service.DocumentParseService;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.infra.messaging.document.DocumentPipelineNonRetryableException;
import com.nexarag.infra.source.ExternalDocumentSourceService;
import com.nexarag.infra.source.model.SourceArtifactBO;
import com.nexarag.infra.source.model.SourceReadRequestDTO;
import com.nexarag.infra.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Locale;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.document.constants.DocumentConstants.*;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.CHUNKING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.INDEXING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.CURRENT_STAGE;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.CURRENT_STATUS;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_ID;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.ROUTE_TARGET;
import static com.nexarag.workflow.util.DocumentIngestionStateUtil.requiredLong;

/**
 * 文档解析节点，负责组合文档状态服务和 infra 解析服务完成解析阶段。
 */
@Component
@RequiredArgsConstructor
public class ParsingNode implements NodeAction {

    private final DocumentService documentService;
    private final DocumentParseService documentParseService;
    private final ExternalDocumentSourceService externalDocumentSourceService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    /**
     * 执行文档解析阶段。
     *
     * @param state Graph 状态
     * @return 解析后的状态增量
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 1. 读取文档并处理幂等短路
        Long documentId = requiredLong(state, DOCUMENT_ID);
        Document document = documentService.getRequiredDocument(documentId);
        Map<String, Object> shortcutState = shortcutWhenAlreadyAdvanced(document);
        if (shortcutState != null) {
            return shortcutState;
        }

        // 2. 校验解析节点允许处理的状态
        validateParseStatus(document);
        markParsing(document);

        // 3. 调用infra解析能力生成标准解析产物，异常交给RocketMQ触发重试
        ParsedArtifact parsedArtifact = parseDocument(document);

        // 4. 回写解析产物并路由到切分节点
        markParsed(document, parsedArtifact);
        return Map.of(
                CURRENT_STAGE, DocumentStatus.PARSING.name(),
                CURRENT_STATUS, DocumentStatus.PARSED.name(),
                ROUTE_TARGET, CHUNKING_NODE
        );
    }

    private Map<String, Object> shortcutWhenAlreadyAdvanced(Document document) {
        DocumentStatus status = document.getStatus();
        if (status == DocumentStatus.PARSED) {
            return Map.of(CURRENT_STATUS, status.name(), ROUTE_TARGET, CHUNKING_NODE);
        }
        if (status == DocumentStatus.CHUNKED || status == DocumentStatus.INDEXING) {
            return Map.of(CURRENT_STATUS, status.name(), ROUTE_TARGET, INDEXING_NODE);
        }
        if (status == DocumentStatus.INDEXED || status == DocumentStatus.FAILED) {
            return Map.of(CURRENT_STATUS, status.name(), ROUTE_TARGET, END);
        }
        return null;
    }

    private void validateParseStatus(Document document) {
        DocumentStatus status = document.getStatus();
        if (status != DocumentStatus.QUEUED && status != DocumentStatus.PARSING) {
            throw new ServiceException("文档状态不允许执行解析，documentId=" + document.getDocumentId()
                    + "，status=" + status, DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
    }

    private void markParsing(Document document) {
        document.setStatus(DocumentStatus.PARSING);
        document.setProcessStartTime(LocalDateTime.now());
        boolean updated = documentService.updateById(document);
        if (!updated) {
            throw new ServiceException("更新文档解析中状态失败，documentId=" + document.getDocumentId(),
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
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

    /**
     * 根据来源选择文件解析器或外部平台 Reader。
     */
    private ParsedArtifact parseDocument(Document document) {
        if (document.getSourceType() == null || document.getSourceType() == ExternalDocumentSourceType.LOCAL) {
            return documentParseService.parse(buildParseRequest(document));
        }
        SourceArtifactBO artifact = externalDocumentSourceService.readAndPersist(new SourceReadRequestDTO(
                document.getDocumentId(), document.getSourceType(), document.getSourceUrl()));
        refreshExternalDocumentName(document, artifact.title());
        return artifact.parsedArtifact();
    }

    /**
     * 将远端文档标题回写为外部文档的默认展示名和原始文件名。
     *
     * @param document    当前处理中的文档实体
     * @param sourceTitle 外部平台返回的文档标题
     */
    private void refreshExternalDocumentName(Document document, String sourceTitle) {
        if (!StringUtils.hasText(sourceTitle)) {
            return;
        }
        String normalizedTitle = sourceTitle.trim();

        String originalFileName = toMarkdownFileName(normalizedTitle);
        validateExternalDocumentName(document.getDocumentId(), normalizedTitle, originalFileName);

        // 1. 仅覆盖系统默认标题，保留用户在提交时明确填写的标题
        if (DEFAULT_EXTERNAL_DOCUMENT_TITLE.equals(document.getTitle())) {
            document.setTitle(normalizedTitle);
        }

        // 2. 外部来源统一解析为 Markdown，文件名使用远端文档名便于追溯
        document.setOriginalFileName(originalFileName);
    }

    private String toMarkdownFileName(String title) {
        return title.toLowerCase(Locale.ROOT).endsWith(MARKDOWN_FILE_EXTENSION)
                ? title : title + MARKDOWN_FILE_EXTENSION;
    }

    private void validateExternalDocumentName(Long documentId, String title, String originalFileName) {
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new DocumentPipelineNonRetryableException("外部文档标题超过最大长度，documentId=" + documentId
                    + "，maxLength=" + MAX_TITLE_LENGTH);
        }
        if (originalFileName.length() > MAX_ORIGINAL_FILE_NAME_LENGTH) {
            throw new DocumentPipelineNonRetryableException("外部文档原始文件名超过最大长度，documentId=" + documentId
                    + "，maxLength=" + MAX_ORIGINAL_FILE_NAME_LENGTH);
        }
    }

    private ParseConfigRequest readParseConfig(String processConfigJson) {
        if (!StringUtils.hasText(processConfigJson)) {
            return null;
        }
        try {
            ProcessDocumentRequest request = objectMapper.readValue(processConfigJson, ProcessDocumentRequest.class);
            return request.parseConfig();
        } catch (JsonProcessingException exception) {
            throw new ServiceException("读取文档解析配置失败", exception,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }

    private void markParsed(Document document, ParsedArtifact parsedArtifact) {
        document.setParsedFileUrl(fileStorageService.resolveUrl(parsedArtifact.objectKey()));
        document.setParsedObjectName(parsedArtifact.objectKey());
        document.setParsedContentType(parsedArtifact.contentType());
        document.setFailureStage(null);
        document.setFailureReason(null);
        document.setFailureDetail(null);
        document.setStatus(DocumentStatus.PARSED);
        boolean updated = documentService.updateById(document);
        if (!updated) {
            throw new ServiceException("更新文档解析完成状态失败，documentId=" + document.getDocumentId(),
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
    }

}
