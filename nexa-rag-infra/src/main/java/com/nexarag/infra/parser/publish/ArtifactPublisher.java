package com.nexarag.infra.parser.publish;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.constants.ParsedContentTypes;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.ExtractedAssetBO;
import com.nexarag.infra.parser.model.ExtractedDocumentBO;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.StoredFile;
import com.nexarag.infra.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析制品发布器，负责流式上传资源和 Markdown，并在失败时清理当前文档的半成品。
 */
@Component
@RequiredArgsConstructor
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
            Map<String, String> assetUrls = publishAssets(artifactDTO.documentId(), documentBO.assets());
            Path rewrittenMarkdown = documentBO.markdownPath().resolveSibling(REWRITTEN_MARKDOWN_FILE);
            markdownAssetFileRewriter.rewrite(documentBO.markdownPath(), rewrittenMarkdown, assetUrls);
            String objectName = objectNameResolver.resolveParsedObjectName(artifactDTO.documentId(),
                    artifactDTO.originalFileName(), ".md");
            try (InputStream inputStream = Files.newInputStream(rewrittenMarkdown)) {
                StoredFile storedFile = fileStorageService.saveAs(objectName, inputStream, Files.size(rewrittenMarkdown),
                        ParsedContentTypes.TEXT_MARKDOWN);
                return ParsedArtifact.builder().objectKey(storedFile.objectName())
                        .contentType(ParsedContentTypes.TEXT_MARKDOWN).metadata(documentBO.metadata()).build();
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

    private Map<String, String> publishAssets(Long documentId, List<ExtractedAssetBO> assets) throws Exception {
        Map<String, String> assetUrls = new LinkedHashMap<>();
        if (assets == null) {
            return assetUrls;
        }
        for (ExtractedAssetBO asset : assets) {
            String objectName = objectNameResolver.resolveParsedAssetObjectName(documentId,
                    asset.file().getFileName().toString());
            try (InputStream inputStream = Files.newInputStream(asset.file())) {
                StoredFile storedFile = fileStorageService.saveAs(objectName, inputStream, Files.size(asset.file()),
                        asset.contentType());
                assetUrls.put(asset.relativePath(), storedFile.url());
            }
        }
        return assetUrls;
    }
}
