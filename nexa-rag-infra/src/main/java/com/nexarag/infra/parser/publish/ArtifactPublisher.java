package com.nexarag.infra.parser.publish;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.constants.ParsedContentTypes;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.ExtractedAssetBO;
import com.nexarag.infra.parser.model.ExtractedDocumentBO;
import com.nexarag.infra.parser.model.ExtractedStructureArtifactBO;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.StoredFile;
import com.nexarag.infra.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解析制品发布器，负责流式上传资源和 Markdown，并在失败时清理当前文档的半成品。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArtifactPublisher {

    private static final String REWRITTEN_MARKDOWN_FILE = "content.rewritten.md";

    private final FileStorageService fileStorageService;
    private final ObjectNameResolver objectNameResolver;
    private final MarkdownAssetFileRewriter markdownAssetFileRewriter;

    /**
     * 发布转换后的资源和主 Markdown 制品。
     *
     * @param artifactDTO 文档处理上下文
     * @param documentBO  文件化转换结果
     * @return 已发布解析制品
     */
    public ParsedArtifact publish(DocumentArtifactDTO artifactDTO, ExtractedDocumentBO documentBO) {
        String parsedPrefix = objectNameResolver.resolveParsedPrefix(artifactDTO.documentId());
        try {
            AssetPublishResult assetPublishResult = publishAssets(artifactDTO.documentId(), documentBO.assets());
            List<Map<String, Object>> structureArtifacts = publishStructureArtifacts(artifactDTO.documentId(),
                    documentBO.structureArtifacts());
            Path rewrittenMarkdown = documentBO.markdownPath().resolveSibling(REWRITTEN_MARKDOWN_FILE);
            markdownAssetFileRewriter.rewrite(documentBO.markdownPath(), rewrittenMarkdown, assetPublishResult.assetUrls(),
                    Set.copyOf(assetPublishResult.failedRelativePaths()));
            String objectName = objectNameResolver.resolveParsedObjectName(artifactDTO.documentId(),
                    artifactDTO.originalFileName(), ".md");
            try (InputStream inputStream = Files.newInputStream(rewrittenMarkdown)) {
                StoredFile storedFile = fileStorageService.saveAs(objectName, inputStream, Files.size(rewrittenMarkdown),
                        ParsedContentTypes.TEXT_MARKDOWN);
                return ParsedArtifact.builder().objectKey(storedFile.objectName())
                        .contentType(ParsedContentTypes.TEXT_MARKDOWN)
                        .metadata(mergeMetadata(documentBO.metadata(), structureArtifacts, assetPublishResult)).build();
            }
        } catch (Exception exception) {
            fileStorageService.deleteByPrefix(parsedPrefix);
            if (exception instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException("发布文档解析制品失败，documentId=" + artifactDTO.documentId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private AssetPublishResult publishAssets(Long documentId, List<ExtractedAssetBO> assets) throws Exception {
        Map<String, String> assetUrls = new LinkedHashMap<>();
        List<String> failedRelativePaths = new ArrayList<>();
        if (assets == null) {
            return new AssetPublishResult(Map.of(), List.of());
        }
        for (ExtractedAssetBO asset : assets) {
            try {
                String objectName = objectNameResolver.resolveParsedAssetObjectName(documentId,
                        asset.file().getFileName().toString());
                try (InputStream inputStream = Files.newInputStream(asset.file())) {
                    StoredFile storedFile = fileStorageService.saveAs(objectName, inputStream, Files.size(asset.file()),
                            asset.contentType());
                    assetUrls.put(asset.relativePath(), storedFile.url());
                }
            } catch (Exception exception) {
                failedRelativePaths.add(asset.relativePath());
                log.warn("发布文档媒体资源失败，documentId={}，relativePath={}", documentId, asset.relativePath(), exception);
            }
        }
        return new AssetPublishResult(Map.copyOf(assetUrls), List.copyOf(failedRelativePaths));
    }

    private List<Map<String, Object>> publishStructureArtifacts(Long documentId,
                                                                 List<ExtractedStructureArtifactBO> artifacts)
            throws Exception {
        List<Map<String, Object>> metadata = new ArrayList<>();
        if (artifacts == null) {
            return metadata;
        }
        for (ExtractedStructureArtifactBO artifact : artifacts) {
            String objectName = objectNameResolver.resolveParsedStructureObjectName(documentId, artifact.relativePath());
            try (InputStream inputStream = Files.newInputStream(artifact.file())) {
                StoredFile storedFile = fileStorageService.saveAs(objectName, inputStream, Files.size(artifact.file()),
                        artifact.contentType());
                metadata.add(Map.of("type", resolveStructureArtifactType(artifact.relativePath()),
                        "objectKey", storedFile.objectName(), "contentType", artifact.contentType(),
                        "size", Files.size(artifact.file())));
            }
        }
        return List.copyOf(metadata);
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> sourceMetadata,
                                               List<Map<String, Object>> structureArtifacts,
                                               AssetPublishResult assetPublishResult) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (sourceMetadata != null) {
            metadata.putAll(sourceMetadata);
        }
        if (!structureArtifacts.isEmpty()) {
            metadata.put("structureArtifacts", structureArtifacts);
        }
        if (!assetPublishResult.failedRelativePaths().isEmpty()) {
            metadata.put("assetPublish", Map.of("failedCount", assetPublishResult.failedRelativePaths().size(),
                    "failedPaths", assetPublishResult.failedRelativePaths()));
        }
        return metadata;
    }

    private String resolveStructureArtifactType(String relativePath) {
        if ("mineru-middle.json".equals(relativePath)) {
            return "MINERU_MIDDLE_JSON";
        }
        if ("mineru-content-list-v2.json".equals(relativePath)) {
            return "MINERU_CONTENT_LIST_V2_JSON";
        }
        return "MINERU_CONTENT_LIST_JSON";
    }

    /** 资源上传的成功 URL 与失败路径。 */
    private record AssetPublishResult(Map<String, String> assetUrls, List<String> failedRelativePaths) {
    }
}
