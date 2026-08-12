package com.nexarag.infra.source;

import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;
import com.nexarag.infra.parser.service.DocumentParseService;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import com.nexarag.infra.parser.workspace.ArtifactWorkspaceFactory;
import com.nexarag.infra.source.model.SourceArtifactBO;
import com.nexarag.infra.source.model.SourceReadRequestDTO;
import com.nexarag.infra.source.model.SourceReadResultBO;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 外部来源读取服务实现，将平台导出的原始文件流式保存为快照，并复用工作区文件完成解析。
 */
@Service
@RequiredArgsConstructor
public class ExternalDocumentSourceServiceImpl implements ExternalDocumentSourceService {

    private final List<ExternalDocumentSourceReader> sourceReaders;
    private final FileStorageService fileStorageService;
    private final ObjectNameResolver objectNameResolver;
    private final ArtifactWorkspaceFactory workspaceFactory;
    private final DocumentParseService documentParseService;

    @Override
    public String validateAndExtractDocumentId(ExternalDocumentSourceType sourceType, String sourceUrl) {
        return requiredReader(sourceType).validateAndExtractDocumentId(sourceUrl);
    }

    @Override
    public SourceArtifactBO readAndPersist(SourceReadRequestDTO request) {
        // 1. 按来源路由并创建受管工作区。
        if (request == null || request.documentId() == null) {
            throw new ServiceException("外部来源读取请求不能为空");
        }
        try (ArtifactWorkspace workspace = workspaceFactory.create(request.documentId())) {
            SourceReadResultBO result = requiredReader(request.sourceType()).read(request, workspace);
            validateSourceResult(result, request.documentId());

            // 2. 将来源文件流式保存为不可变快照。
            String snapshotName = objectNameResolver.resolveSourceSnapshotObjectName(request.documentId(),
                    resolveExtension(result.originalFileName()));
            try (InputStream sourceStream = Files.newInputStream(result.sourcePath())) {
                fileStorageService.saveAs(snapshotName, sourceStream, Files.size(result.sourcePath()),
                        result.sourceContentType());
            }

            // 3. 复用同一工作区文件生成解析制品。
            DocumentArtifactDTO artifactDTO = DocumentArtifactDTO.builder()
                    .documentId(request.documentId())
                    .originalFileName(result.originalFileName())
                    .format(result.documentFormat())
                    .originalObjectName(snapshotName)
                    .build();
            ParsedArtifact parsedArtifact = documentParseService.parseStaged(artifactDTO,
                    new StagedDocumentBO(result.sourcePath(), workspace));
            return new SourceArtifactBO(parsedArtifact, result.title(), snapshotName, result.metadata());
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("保存外部来源文件失败，documentId=" + request.documentId(), exception,
                    BaseErrorCode.SERVICE_ERROR);
        }
    }

    private ExternalDocumentSourceReader requiredReader(ExternalDocumentSourceType sourceType) {
        if (sourceType == null || sourceType == ExternalDocumentSourceType.LOCAL) {
            throw new ServiceException("本地上传不应使用外部来源读取服务");
        }
        return sourceReaders.stream().filter(reader -> reader.supports(sourceType)).findFirst()
                .orElseThrow(() -> new ServiceException("未找到外部来源读取器，sourceType=" + sourceType));
    }

    /**
     * 校验 Reader 返回的文件化结果。
     */
    private void validateSourceResult(SourceReadResultBO result, Long documentId) {
        if (result == null || result.sourcePath() == null || !Files.isRegularFile(result.sourcePath())
                || result.documentFormat() == null || !StringUtils.hasText(result.originalFileName())
                || !StringUtils.hasText(result.sourceContentType())) {
            throw new ServiceException("外部来源未返回有效文件，documentId=" + documentId);
        }
    }

    /**
     * 从受控原始文件名取得对象快照扩展名。
     */
    private String resolveExtension(String originalFileName) {
        int extensionIndex = originalFileName.lastIndexOf('.');
        return extensionIndex >= 0 ? originalFileName.substring(extensionIndex) : ".bin";
    }
}
