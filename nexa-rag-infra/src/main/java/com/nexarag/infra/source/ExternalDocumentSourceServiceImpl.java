package com.nexarag.infra.source;

import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.source.model.SourceArtifactBO;
import com.nexarag.infra.source.model.SourceReadRequestDTO;
import com.nexarag.infra.source.model.SourceReadResultBO;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 外部来源读取服务实现，将平台读取结果统一保存为快照和 Markdown 制品。
 */
@Service
@RequiredArgsConstructor
public class ExternalDocumentSourceServiceImpl implements ExternalDocumentSourceService {

    private static final String MARKDOWN_CONTENT_TYPE = "text/markdown";

    private final List<ExternalDocumentSourceReader> sourceReaders;
    private final FileStorageService fileStorageService;
    private final ObjectNameResolver objectNameResolver;

    @Override
    public String validateAndExtractDocumentId(ExternalDocumentSourceType sourceType, String sourceUrl) {
        return requiredReader(sourceType).validateAndExtractDocumentId(sourceUrl);
    }

    @Override
    public SourceArtifactBO readAndPersist(SourceReadRequestDTO request) {
        // 1. 按来源路由并读取远端内容
        if (request == null || request.documentId() == null) {
            throw new ServiceException("外部来源读取请求不能为空");
        }
        SourceReadResultBO result = requiredReader(request.sourceType()).read(request);
        if (result == null || !StringUtils.hasText(result.markdownContent())) {
            throw new ServiceException("外部来源未返回可切分Markdown，documentId=" + request.documentId());
        }

        // 2. 保存不可变来源快照和规范化 Markdown
        byte[] snapshot = result.snapshotContent() == null ? new byte[0] : result.snapshotContent();
        String snapshotName = objectNameResolver.resolveSourceSnapshotObjectName(request.documentId(), ".json");
        fileStorageService.saveAs(snapshotName, new ByteArrayInputStream(snapshot), snapshot.length,
                result.snapshotContentType());
        byte[] markdown = result.markdownContent().getBytes(StandardCharsets.UTF_8);
        String parsedName = objectNameResolver.resolveParsedObjectName(request.documentId(), "source.md", ".md");
        fileStorageService.saveAs(parsedName, new ByteArrayInputStream(markdown), markdown.length, MARKDOWN_CONTENT_TYPE);

        // 3. 返回工作流可回写的标准制品
        return new SourceArtifactBO(ParsedArtifact.builder().objectKey(parsedName)
                .contentType(MARKDOWN_CONTENT_TYPE).metadata(result.metadata()).build(), result.title(),
                snapshotName, result.metadata());
    }

    private ExternalDocumentSourceReader requiredReader(ExternalDocumentSourceType sourceType) {
        if (sourceType == null || sourceType == ExternalDocumentSourceType.LOCAL) {
            throw new ServiceException("本地上传不应使用外部来源读取服务");
        }
        return sourceReaders.stream().filter(reader -> reader.supports(sourceType)).findFirst()
                .orElseThrow(() -> new ServiceException("未找到外部来源读取器，sourceType=" + sourceType));
    }
}
