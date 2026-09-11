package com.nexarag.infra.parser.markdown;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.parser.handler.DocumentArtifactHandler;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ExtractedAssetBO;
import com.nexarag.infra.parser.model.ExtractedDocumentBO;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;
import com.nexarag.infra.parser.publish.ArtifactPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Markdown 制品处理器，发布正文及来源读取阶段已写入工作区的媒体资源。
 */
@Component
@RequiredArgsConstructor
public class MarkdownArtifactHandler implements DocumentArtifactHandler {

    private final ArtifactPublisher artifactPublisher;

    /** {@inheritDoc} */
    @Override
    public Set<DocumentFormat> supportedFormats() {
        return Set.of(DocumentFormat.MARKDOWN);
    }

    /** {@inheritDoc} */
    @Override
    public ParsedArtifact handle(DocumentArtifactDTO artifactDTO, StagedDocumentBO stagedDocumentBO) {
        try {
            Path markdownPath = stagedDocumentBO.sourcePath();
            List<ExtractedAssetBO> assets = collectAssets(markdownPath);
            return artifactPublisher.publish(artifactDTO, new ExtractedDocumentBO(markdownPath, assets,
                    Map.of("parser", "markdown")));
        } catch (IOException exception) {
            throw new ServiceException("收集Markdown媒体资源失败，documentId=" + artifactDTO.documentId(), exception,
                    BaseErrorCode.SERVICE_ERROR);
        }
    }

    private List<ExtractedAssetBO> collectAssets(Path markdownPath) throws IOException {
        Path assetsDirectory = markdownPath.getParent().resolve("assets").normalize();
        if (!Files.isDirectory(assetsDirectory) || Files.isSymbolicLink(assetsDirectory)) {
            return List.of();
        }
        List<ExtractedAssetBO> assets = new ArrayList<>();
        try (Stream<Path> files = Files.walk(assetsDirectory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path normalizedFile = file.normalize();
                if (Files.isSymbolicLink(normalizedFile) || !normalizedFile.startsWith(assetsDirectory)) {
                    throw new ServiceException("Markdown媒体资源路径不合法，file=" + file);
                }
                assets.add(new ExtractedAssetBO(normalizedFile,
                        markdownPath.getParent().relativize(normalizedFile).toString().replace('\\', '/'),
                        resolveContentType(normalizedFile)));
            }
        }
        return List.copyOf(assets);
    }

    private String resolveContentType(Path file) throws IOException {
        String detected = Files.probeContentType(file);
        return detected == null ? "application/octet-stream" : detected;
    }
}
