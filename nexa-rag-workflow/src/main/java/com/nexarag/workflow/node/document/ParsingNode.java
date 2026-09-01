package com.nexarag.workflow.node.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.model.dto.ParseConfigRequest;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.infra.messaging.document.DocumentPipelineNonRetryableException;
import com.nexarag.infra.parser.model.DocumentParseRequest;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.service.DocumentParseService;
import com.nexarag.infra.source.ExternalDocumentSourceService;
import com.nexarag.infra.source.model.SourceArtifactBO;
import com.nexarag.infra.source.model.SourceReadRequestDTO;
import com.nexarag.infra.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.document.constants.DocumentConstants.*;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.CHUNKING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.INDEXING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.*;
import static com.nexarag.workflow.util.DocumentIngestionStateUtil.requiredLong;

/**
 * 文档解析节点，负责组合文档状态服务和 infra 解析服务完成解析阶段。
 */
@Component
@RequiredArgsConstructor
public class ParsingNode implements NodeAction {

    private final DocumentService documentService;
    private final DocumentVersionService documentVersionService;
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
        // 1. 读取文档版本并处理幂等短路
        Long documentId = requiredLong(state, DOCUMENT_ID);
        Long documentVersionId = requiredLong(state, DOCUMENT_VERSION_ID);
        DocumentVersionDO documentVersion = documentVersionService.getRequiredVersion(documentId, documentVersionId);
        Map<String, Object> shortcutState = shortcutWhenAlreadyAdvanced(documentVersion);
        if (shortcutState != null) {
            return shortcutState;
        }

        // 2. 校验解析节点允许处理的状态
        validateParseStatus(documentVersion);
        markParsing(documentVersion);

        // 3. 调用infra解析能力生成标准解析产物，异常交给RocketMQ触发重试
        ParsedArtifact parsedArtifact = parseDocument(documentVersion);

        // 4. 回写解析产物并路由到切分节点
        markParsed(documentVersion, parsedArtifact);
        return Map.of(
                CURRENT_STAGE, DocumentVersionStatus.PARSING.name(),
                CURRENT_STATUS, DocumentVersionStatus.PARSED.name(),
                ROUTE_TARGET, CHUNKING_NODE
        );
    }

    private Map<String, Object> shortcutWhenAlreadyAdvanced(DocumentVersionDO documentVersion) {
        DocumentVersionStatus status = documentVersion.getStatus();
        if (status == DocumentVersionStatus.PARSED) {
            return Map.of(CURRENT_STATUS, status.name(), ROUTE_TARGET, CHUNKING_NODE);
        }
        if (status == DocumentVersionStatus.CHUNKED || status == DocumentVersionStatus.INDEXING) {
            return Map.of(CURRENT_STATUS, status.name(), ROUTE_TARGET, INDEXING_NODE);
        }
        if (status == DocumentVersionStatus.INDEX_READY || status == DocumentVersionStatus.FAILED) {
            return Map.of(CURRENT_STATUS, status.name(), ROUTE_TARGET, END);
        }
        return null;
    }

    private void validateParseStatus(DocumentVersionDO documentVersion) {
        DocumentVersionStatus status = documentVersion.getStatus();
        if (status != DocumentVersionStatus.QUEUED && status != DocumentVersionStatus.PARSING) {
            throw new ServiceException("文档版本状态不允许执行解析，documentId=" + documentVersion.getDocumentId()
                    + "，status=" + status, DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
    }

    private void markParsing(DocumentVersionDO documentVersion) {
        documentVersion.setStatus(DocumentVersionStatus.PARSING);
        documentVersion.setProcessStartTime(LocalDateTime.now());
        boolean updated = documentVersionService.updateById(documentVersion);
        if (!updated) {
            throw new ServiceException("更新文档版本解析中状态失败，documentId=" + documentVersion.getDocumentId(),
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
    }

    private DocumentParseRequest buildParseRequest(DocumentVersionDO documentVersion) {
        ParseConfigRequest parseConfig = readParseConfig(documentVersion.getProcessConfigJson());
        return DocumentParseRequest.builder()
                .documentId(documentVersion.getDocumentId())
                .originalFileName(documentVersion.getOriginalFileName())
                .fileType(documentVersion.getFileType().name())
                .originalObjectName(documentVersion.getOriginalObjectName())
                .originalFileUrl(documentVersion.getOriginalFileUrl())
                .enableOcr(parseConfig == null ? null : parseConfig.enableOcr())
                .enableImageDescription(parseConfig == null ? null : parseConfig.enableImageDescription())
                .build();
    }

    /**
     * 根据来源选择文件解析器或外部平台 Reader。
     */
    private ParsedArtifact parseDocument(DocumentVersionDO documentVersion) {
        if (documentVersion.getSourceType() == null || documentVersion.getSourceType() == ExternalDocumentSourceType.LOCAL) {
            return documentParseService.parse(buildParseRequest(documentVersion));
        }
        SourceArtifactBO artifact = externalDocumentSourceService.readAndPersist(new SourceReadRequestDTO(
                documentVersion.getDocumentId(), documentVersion.getSourceType(), documentVersion.getSourceUrl()));
        refreshExternalDocumentName(documentVersion, artifact.title());
        return artifact.parsedArtifact();
    }

    /**
     * 将远端文档标题回写为外部文档的默认展示名和原始文件名。
     *
     * @param document    当前处理中的文档实体
     * @param sourceTitle 外部平台返回的文档标题
     */
    private void refreshExternalDocumentName(DocumentVersionDO documentVersion, String sourceTitle) {
        if (!StringUtils.hasText(sourceTitle)) {
            return;
        }
        String normalizedTitle = sourceTitle.trim();

        String originalFileName = toMarkdownFileName(normalizedTitle);
        validateExternalDocumentName(documentVersion.getDocumentId(), normalizedTitle, originalFileName);

        // 1. 标题属于文档稳定身份信息，仅在默认标题场景首次回写。
        Document document = documentService.getRequiredDocument(documentVersion.getDocumentId());
        if (DEFAULT_EXTERNAL_DOCUMENT_TITLE.equals(document.getTitle())) {
            document.setTitle(normalizedTitle);
            documentService.updateById(document);
        }

        // 2. 外部来源的文件名属于版本快照，仅更新当前处理版本。
        documentVersion.setOriginalFileName(originalFileName);
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

    private void markParsed(DocumentVersionDO documentVersion, ParsedArtifact parsedArtifact) {
        try {
            documentVersion.setParsedMetadataJson(objectMapper.writeValueAsString(
                    parsedArtifact.metadata() == null ? Map.of() : parsedArtifact.metadata()));
        } catch (JsonProcessingException exception) {
            throw new ServiceException("保存文档解析元数据失败", exception,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
        documentVersion.setParsedFileUrl(fileStorageService.resolveUrl(parsedArtifact.objectKey()));
        documentVersion.setParsedObjectName(parsedArtifact.objectKey());
        documentVersion.setParsedContentType(parsedArtifact.contentType());
        documentVersion.setFailureStage(null);
        documentVersion.setFailureReason(null);
        documentVersion.setFailureDetail(null);
        documentVersion.setStatus(DocumentVersionStatus.PARSED);
        boolean updated = documentVersionService.updateById(documentVersion);
        if (!updated) {
            throw new ServiceException("更新文档版本解析完成状态失败，documentId=" + documentVersion.getDocumentId(),
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
    }

}
