package com.nexarag.infra.parser.tika;

import com.alibaba.cloud.ai.parser.tika.TikaDocumentParser;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.constants.ParsedContentTypes;
import com.nexarag.infra.parser.handler.DocumentArtifactHandler;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.StoredFile;
import com.nexarag.infra.storage.service.FileStorageService;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tika 文档制品处理器，负责将 PPT 和纯文本文件抽取为标准文本产物。
 */
@Component
public class TikaArtifactParser implements DocumentArtifactHandler {

    private final FileStorageService fileStorageService;
    private final ObjectNameResolver objectNameResolver;
    private final TikaDocumentParser parser;

    /**
     * 创建 Tika 文档解析适配器。
     *
     * @param fileStorageService 文件存储服务
     * @param objectNameResolver 对象名解析器
     */
    public TikaArtifactParser(FileStorageService fileStorageService, ObjectNameResolver objectNameResolver) {
        this.fileStorageService = fileStorageService;
        this.objectNameResolver = objectNameResolver;
        this.parser = new TikaDocumentParser();
    }

    /**
     * 返回 Tika 支持的文件格式。
     *
     * @return 支持的文件格式集合
     */
    @Override
    public Set<DocumentFormat> supportedFormats() {
        return Set.of(DocumentFormat.PPT, DocumentFormat.TEXT);
    }

    /**
     * 使用 Tika 从已暂存文件抽取文本并保存解析产物。
     *
     * @param artifactDTO 文档处理上下文
     * @param stagedDocumentBO 已暂存原始文档
     * @return 文档解析结果
     */
    @Override
    public ParsedArtifact handle(DocumentArtifactDTO artifactDTO, StagedDocumentBO stagedDocumentBO) {
        try (InputStream inputStream = Files.newInputStream(stagedDocumentBO.sourcePath())) {
            // 1. 使用框架解析器抽取文本内容
            List<Document> documents = parser.parse(inputStream);
            String content = mergeDocumentTexts(documents);
            if (!StringUtils.hasText(content)) {
                throw new ServiceException("Tika解析结果为空，documentId=" + artifactDTO.documentId());
            }

            // 2. 保存标准文本解析产物
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            String parsedObjectName = objectNameResolver.resolveParsedObjectName(
                    artifactDTO.documentId(), artifactDTO.originalFileName(), ".txt");
            StoredFile storedFile = fileStorageService.saveAs(parsedObjectName,
                    new java.io.ByteArrayInputStream(contentBytes), contentBytes.length, ParsedContentTypes.TEXT_PLAIN);

            // 3. 组装解析结果
            return ParsedArtifact.builder()
                    .contentType(ParsedContentTypes.TEXT_PLAIN)
                    .objectKey(storedFile.objectName())
                    .metadata(Map.of(
                            "parser", "tika",
                            "originalFileName", artifactDTO.originalFileName(),
                            "parsedDocumentCount", documents == null ? 0 : documents.size(),
                            "textLength", content.length()
                    ))
                    .build();
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("Tika解析文档失败，documentId=" + artifactDTO.documentId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    static String mergeDocumentTexts(List<Document> documents) {
        if (documents == null) {
            return "";
        }
        return documents.stream()
                .filter(Objects::nonNull)
                .map(Document::getText)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.joining("\n\n"));
    }
}
