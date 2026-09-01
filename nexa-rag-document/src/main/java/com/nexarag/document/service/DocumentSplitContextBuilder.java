package com.nexarag.document.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.model.bo.split.DocumentSplitContext;
import com.nexarag.document.model.bo.structure.StructureArtifactReferenceBO;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.dto.SplitConfigRequest;
import com.nexarag.document.model.dto.UploadDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.infra.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档切分上下文构建器，负责读取对象存储中的切分输入。
 */
@Component
@RequiredArgsConstructor
public class DocumentSplitContextBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FileStorageService fileStorageService;
    private final ProcessConfigDefaults processConfigDefaults;

    /**
     * 使用文档稳定元数据与文档版本快照构建切分上下文。
     *
     * @param document        文档稳定身份信息
     * @param documentVersion 本次处理的文件与解析产物快照
     * @return 文档切分上下文
     */
    public DocumentSplitContext build(Document document, DocumentVersionDO documentVersion) {
        SplitConfigRequest splitConfig = readSplitConfig(documentVersion);
        if (documentVersion.getFileType() == FileType.EXCEL) {
            return buildBinaryContext(document, documentVersion, splitConfig);
        }
        return buildTextContext(document, documentVersion, splitConfig);
    }

    private DocumentSplitContext buildTextContext(Document document, DocumentVersionDO documentVersion,
                                                  SplitConfigRequest splitConfig) {
        String objectName = StringUtils.hasText(documentVersion.getParsedObjectName())
                ? documentVersion.getParsedObjectName() : documentVersion.getOriginalObjectName();
        return baseContext(document, documentVersion, splitConfig,
                new String(loadBytes(objectName), StandardCharsets.UTF_8), null);
    }

    private DocumentSplitContext buildBinaryContext(Document document, DocumentVersionDO documentVersion,
                                                    SplitConfigRequest splitConfig) {
        return baseContext(document, documentVersion, splitConfig, null,
                loadBytes(documentVersion.getOriginalObjectName()));
    }

    private DocumentSplitContext baseContext(Document document, DocumentVersionDO documentVersion,
                                             SplitConfigRequest splitConfig, String content, byte[] fileBytes) {
        return new DocumentSplitContext(document.getDocumentId(), document.getTitle(), documentVersion.getOriginalFileName(),
                documentVersion.getFileType(), documentVersion.getOriginalObjectName(), documentVersion.getOriginalFileUrl(),
                documentVersion.getParsedObjectName(), documentVersion.getParsedFileUrl(), documentVersion.getParsedContentType(),
                content, fileBytes, splitConfig, readStructureArtifacts(documentVersion));
    }

    private List<StructureArtifactReferenceBO> readStructureArtifacts(DocumentVersionDO documentVersion) {
        if (!StringUtils.hasText(documentVersion.getParsedMetadataJson())) {
            return List.of();
        }
        try {
            JsonNode rawArtifacts = OBJECT_MAPPER.readTree(documentVersion.getParsedMetadataJson()).path("structureArtifacts");
            if (!rawArtifacts.isArray()) {
                return List.of();
            }
            List<StructureArtifactReferenceBO> references = new ArrayList<>();
            for (JsonNode artifact : rawArtifacts) {
                String type = textValue(artifact, "type");
                String objectKey = textValue(artifact, "objectKey");
                String contentType = textValue(artifact, "contentType");
                Long size = nonNegativeLongValue(artifact.path("size"));
                if (("MINERU_MIDDLE_JSON".equals(type) || "MINERU_CONTENT_LIST_JSON".equals(type)
                        || "MINERU_CONTENT_LIST_V2_JSON".equals(type))
                        && StringUtils.hasText(objectKey) && "application/json".equals(contentType) && size != null) {
                    references.add(new StructureArtifactReferenceBO(type, objectKey, contentType, size));
                }
            }
            return List.copyOf(references);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("读取文档版本解析元数据失败，documentId=" + documentVersion.getDocumentId(), exception,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isTextual() ? value.textValue() : null;
    }

    /**
     * 兼容 MySQL JSON 聚合后被序列化为字符串的文件大小字段。
     */
    private Long nonNegativeLongValue(JsonNode value) {
        if (value.canConvertToLong()) {
            long size = value.longValue();
            return size >= 0 ? size : null;
        }
        if (!value.isTextual() || !value.textValue().matches("\\d+")) {
            return null;
        }
        try {
            return Long.parseLong(value.textValue());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private SplitConfigRequest readSplitConfig(DocumentVersionDO documentVersion) {
        ProcessDocumentRequest request = null;
        if (StringUtils.hasText(documentVersion.getProcessConfigJson())) {
            try {
                request = OBJECT_MAPPER.readValue(documentVersion.getProcessConfigJson(), ProcessDocumentRequest.class);
            } catch (JsonProcessingException exception) {
                throw new ServiceException("读取文档版本切分配置失败，documentId=" + documentVersion.getDocumentId(), exception,
                        DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
            }
        }
        UploadDocumentRequest uploadRequest = new UploadDocumentRequest(null, null,
                request == null ? null : request.splitConfig(), request == null ? null : request.parseConfig(),
                request == null ? null : request.indexConfig());
        return processConfigDefaults.merge(documentVersion.getFileType(), uploadRequest).splitConfig();
    }

    private byte[] loadBytes(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            throw new ServiceException("文档对象名不能为空", DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
        try (InputStream inputStream = fileStorageService.load(objectName)) {
            return inputStream.readAllBytes();
        } catch (IOException exception) {
            throw new ServiceException("读取文档对象失败，objectName=" + objectName, exception,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }
}
