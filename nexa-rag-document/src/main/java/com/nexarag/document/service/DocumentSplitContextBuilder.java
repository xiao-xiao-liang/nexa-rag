package com.nexarag.document.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.dto.SplitConfigRequest;
import com.nexarag.document.model.dto.UploadDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.model.bo.split.DocumentSplitContext;
import com.nexarag.document.model.bo.structure.StructureArtifactReferenceBO;
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
     * 构建文档切分上下文。
     *
     * @param document 文档实体
     * @return 文档切分上下文
     */
    public DocumentSplitContext build(Document document) {
        SplitConfigRequest splitConfig = readSplitConfig(document);
        if (document.getFileType() == FileType.EXCEL) {
            return buildBinaryContext(document, splitConfig);
        }
        return buildTextContext(document, splitConfig);
    }

    private DocumentSplitContext buildTextContext(Document document, SplitConfigRequest splitConfig) {
        String objectName = StringUtils.hasText(document.getParsedObjectName())
                ? document.getParsedObjectName()
                : document.getOriginalObjectName();
        byte[] bytes = loadBytes(objectName);
        String content = new String(bytes, StandardCharsets.UTF_8);
        return baseContext(document, splitConfig, content, null);
    }

    private DocumentSplitContext buildBinaryContext(Document document, SplitConfigRequest splitConfig) {
        byte[] fileBytes = loadBytes(document.getOriginalObjectName());
        return baseContext(document, splitConfig, null, fileBytes);
    }

    private DocumentSplitContext baseContext(Document document,
                                             SplitConfigRequest splitConfig,
                                             String content,
                                             byte[] fileBytes) {
        return new DocumentSplitContext(document.getDocumentId(), document.getTitle(), document.getOriginalFileName(),
                document.getFileType(), document.getOriginalObjectName(), document.getOriginalFileUrl(),
                document.getParsedObjectName(), document.getParsedFileUrl(), document.getParsedContentType(),
                content, fileBytes, splitConfig, readStructureArtifacts(document));
    }

    /** 只将白名单字段转换为结构制品引用，不读取 JSON 正文。 */
    private List<StructureArtifactReferenceBO> readStructureArtifacts(Document document) {
        if (!StringUtils.hasText(document.getParsedMetadataJson())) {
            return List.of();
        }
        try {
            JsonNode rawArtifacts = OBJECT_MAPPER.readTree(document.getParsedMetadataJson()).path("structureArtifacts");
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
                        && StringUtils.hasText(objectKey) && "application/json".equals(contentType)
                        && size != null) {
                    references.add(new StructureArtifactReferenceBO(type, objectKey, contentType, size));
                }
            }
            return List.copyOf(references);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("读取文档解析元数据失败，documentId=" + document.getDocumentId(), exception,
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

    private SplitConfigRequest readSplitConfig(Document document) {
        ProcessDocumentRequest request = null;
        if (StringUtils.hasText(document.getProcessConfigJson())) {
            try {
                request = OBJECT_MAPPER.readValue(document.getProcessConfigJson(), ProcessDocumentRequest.class);
            } catch (JsonProcessingException exception) {
                throw new ServiceException("读取文档切分配置失败，documentId=" + document.getDocumentId(), exception,
                        DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
            }
        }
        UploadDocumentRequest uploadRequest = new UploadDocumentRequest(null, null,
                request == null ? null : request.splitConfig(),
                request == null ? null : request.parseConfig(),
                request == null ? null : request.indexConfig());
        return processConfigDefaults.merge(document.getFileType(), uploadRequest).splitConfig();
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
