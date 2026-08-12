package com.nexarag.infra.parser.pandoc;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.config.PandocProperties;
import com.nexarag.infra.messaging.document.DocumentPipelineNonRetryableException;
import com.nexarag.infra.parser.converter.DocumentConverter;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ExtractedAssetBO;
import com.nexarag.infra.parser.model.ExtractedDocumentBO;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** DOCX 的 Pandoc 转换器，输出工作区内经校验的 Markdown 与媒体文件。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.parser.pandoc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PandocDocxConverter implements DocumentConverter {

    private static final int MAX_ASSET_COUNT = 10_000;

    private final PandocProperties properties;
    private final PandocProcessRunner processRunner;
    private final ArtifactProcessingProperties artifactProcessingProperties;

    /**
     * 返回 Pandoc 当前支持的文档格式。
     *
     * @return 仅支持 Word 文档
     */
    @Override
    public Set<DocumentFormat> supportedFormats() {
        return Set.of(DocumentFormat.WORD);
    }

    /**
     * 调用 Pandoc 将已阶段化的 DOCX 转换为 Markdown，并收集媒体制品。
     *
     * @param artifactDTO 文档处理上下文
     * @param stagedSource 工作区内的 DOCX 文件
     * @param workspace 当前解析工作区
     * @return Markdown 和媒体文件清单
     */
    @Override
    public ExtractedDocumentBO convert(DocumentArtifactDTO artifactDTO, Path stagedSource, ArtifactWorkspace workspace) {
        Path markdownPath = workspace.resolve("content.md");
        Path assetsDirectory = workspace.resolve("assets");
        try {
            Files.createDirectories(assetsDirectory);
            processRunner.run(buildCommand(stagedSource, assetsDirectory, markdownPath), workspace.root());
            validateMarkdown(markdownPath);
            List<ExtractedAssetBO> assets = collectAssets(workspace, assetsDirectory, Files.size(markdownPath));
            return new ExtractedDocumentBO(markdownPath, assets, Map.of("parser", "pandoc"));
        } catch (DocumentPipelineNonRetryableException | ServiceException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ServiceException("Pandoc转换DOCX失败，documentId=" + artifactDTO.documentId(), exception,
                    BaseErrorCode.SERVICE_ERROR);
        }
    }

    private List<String> buildCommand(Path stagedSource, Path assetsDirectory, Path markdownPath) {
        return List.of(properties.getExecutable(), stagedSource.toString(), "--from=docx", "--to=markdown",
                "--wrap=none", "--extract-media=" + assetsDirectory.getFileName(), "--output=" + markdownPath);
    }

    private void validateMarkdown(Path markdownPath) throws IOException {
        if (!Files.isRegularFile(markdownPath) || Files.isSymbolicLink(markdownPath)) {
            throw new ServiceException("Pandoc未生成有效Markdown文件");
        }
        if (Files.size(markdownPath) > requiredWorkspaceLimit()) {
            throw new DocumentPipelineNonRetryableException("Pandoc生成的Markdown超过工作区大小限制");
        }
    }

    private List<ExtractedAssetBO> collectAssets(ArtifactWorkspace workspace, Path assetsDirectory, long totalBytes)
            throws IOException {
        long accumulatedBytes = totalBytes;
        List<ExtractedAssetBO> assets = new ArrayList<>();
        try (Stream<Path> files = Files.walk(assetsDirectory)) {
            var iterator = files.iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                if (Files.isSymbolicLink(file)) {
                    throw new DocumentPipelineNonRetryableException("Pandoc媒体目录包含符号链接");
                }
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                if (assets.size() >= MAX_ASSET_COUNT) {
                    throw new DocumentPipelineNonRetryableException("Pandoc媒体文件数量超过限制");
                }
                long fileSize = Files.size(file);
                accumulatedBytes = Math.addExact(accumulatedBytes, fileSize);
                if (accumulatedBytes > requiredWorkspaceLimit()) {
                    throw new DocumentPipelineNonRetryableException("Pandoc转换制品超过工作区大小限制");
                }
                assets.add(new ExtractedAssetBO(file, workspace.root().relativize(file).toString().replace('\\', '/'),
                        resolveContentType(file)));
            }
        }
        return assets;
    }

    private String resolveContentType(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/png";
    }

    private long requiredWorkspaceLimit() {
        long maxWorkspaceBytes = artifactProcessingProperties.getMaxWorkspaceBytes();
        if (maxWorkspaceBytes <= 0) {
            throw new ServiceException("文档解析工作区大小限制必须大于零");
        }
        return maxWorkspaceBytes;
    }
}
