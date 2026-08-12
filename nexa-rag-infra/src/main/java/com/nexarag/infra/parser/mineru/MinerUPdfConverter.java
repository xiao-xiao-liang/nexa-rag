package com.nexarag.infra.parser.mineru;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.parser.converter.DocumentConverter;
import com.nexarag.infra.parser.mineru.client.MinerUClient;
import com.nexarag.infra.parser.mineru.extract.MinerUZipFileExtractor;
import com.nexarag.infra.parser.mineru.ratelimit.MinerUParseLimiter;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ExtractedDocumentBO;
import com.nexarag.infra.parser.model.MinerUParseCommand;
import com.nexarag.infra.parser.model.MinerUParseResponse;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * MinerU PDF 转换器，使用已暂存的 PDF 文件调用 MinerU，并将 ZIP 响应逐条目写入工作区。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.parser.mineru", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MinerUPdfConverter implements DocumentConverter {

    private final MinerUClient minerUClient;
    private final MinerUZipFileExtractor zipFileExtractor;
    private final MinerUParseLimiter minerUParseLimiter;
    private final ArtifactProcessingProperties artifactProcessingProperties;

    /**
     * 返回 MinerU 当前支持的文件格式。
     *
     * @return PDF 格式集合
     */
    @Override
    public Set<DocumentFormat> supportedFormats() {
        return Set.of(DocumentFormat.PDF);
    }

    /**
     * 调用 MinerU 解析已暂存 PDF，并返回文件化结果。
     *
     * @param artifactDTO 文档处理上下文
     * @param stagedSource 已暂存 PDF 路径
     * @param workspace 当前任务工作区
     * @return 文件化解析结果
     */
    @Override
    public ExtractedDocumentBO convert(DocumentArtifactDTO artifactDTO, Path stagedSource,
                                       ArtifactWorkspace workspace) {
        // 1. 校验输入文件和工作区容量限制。
        if (artifactDTO == null || artifactDTO.documentId() == null || !Files.isRegularFile(stagedSource)) {
            throw new ServiceException("MinerU PDF 转换输入不完整");
        }
        long maxWorkspaceBytes = artifactProcessingProperties.getMaxWorkspaceBytes();
        if (maxWorkspaceBytes <= 0) {
            throw new ServiceException("文档解析工作区大小限制必须大于零");
        }

        // 2. 在全局限流范围内，以文件输入流调用 MinerU。
        return minerUParseLimiter.execute(artifactDTO.documentId(), () -> doConvert(artifactDTO, stagedSource,
                workspace, maxWorkspaceBytes));
    }

    /**
     * 执行单次 PDF 转换。
     */
    private ExtractedDocumentBO doConvert(DocumentArtifactDTO artifactDTO, Path stagedSource,
                                          ArtifactWorkspace workspace, long maxWorkspaceBytes) {
        try (InputStream sourceStream = Files.newInputStream(stagedSource)) {
            MinerUParseResponse response = minerUClient.parse(MinerUParseCommand.builder()
                    .documentId(artifactDTO.documentId())
                    .fileName(artifactDTO.originalFileName())
                    .inputStream(sourceStream)
                    .enableOcr(Boolean.TRUE.equals(artifactDTO.enableOcr()))
                    .build());
            if (response == null || response.zipInputStream() == null) {
                throw new ServiceException("MinerU 未返回 ZIP 解析产物，documentId=" + artifactDTO.documentId());
            }
            return zipFileExtractor.extract(response.zipInputStream(), workspace, maxWorkspaceBytes);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("MinerU 转换 PDF 失败，documentId=" + artifactDTO.documentId(), exception,
                    BaseErrorCode.SERVICE_ERROR);
        }
    }
}
