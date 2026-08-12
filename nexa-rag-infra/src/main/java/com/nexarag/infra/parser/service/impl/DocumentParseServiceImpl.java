package com.nexarag.infra.parser.service.impl;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.parser.handler.DocumentArtifactHandler;
import com.nexarag.infra.parser.handler.DocumentArtifactHandlerRegistry;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.DocumentParseRequest;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;
import com.nexarag.infra.parser.service.DocumentParseService;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import com.nexarag.infra.parser.workspace.ArtifactWorkspaceFactory;
import com.nexarag.infra.parser.workspace.BoundedFileTransfer;
import com.nexarag.infra.storage.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Semaphore;

/**
 * 文档解析应用服务，将工作流请求转换为制品处理上下文并按格式路由到唯一处理器。
 */
@Slf4j
@Service
public class DocumentParseServiceImpl implements DocumentParseService {

    private final DocumentArtifactHandlerRegistry handlerRegistry;
    private final FileStorageService fileStorageService;
    private final ArtifactWorkspaceFactory workspaceFactory;
    private final BoundedFileTransfer boundedFileTransfer;
    private final ArtifactProcessingProperties artifactProcessingProperties;
    private final Semaphore parseSemaphore;

    /**
     * 创建文档解析应用服务，并根据配置建立公平的本地并发边界。
     */
    public DocumentParseServiceImpl(DocumentArtifactHandlerRegistry handlerRegistry,
                                    FileStorageService fileStorageService,
                                    ArtifactWorkspaceFactory workspaceFactory,
                                    BoundedFileTransfer boundedFileTransfer,
                                    ArtifactProcessingProperties artifactProcessingProperties) {
        this.handlerRegistry = handlerRegistry;
        this.fileStorageService = fileStorageService;
        this.workspaceFactory = workspaceFactory;
        this.boundedFileTransfer = boundedFileTransfer;
        this.artifactProcessingProperties = artifactProcessingProperties;
        int maxConcurrent = artifactProcessingProperties.getMaxConcurrent();
        if (maxConcurrent <= 0) {
            throw new ServiceException("文档解析最大并发数必须大于零");
        }
        this.parseSemaphore = new Semaphore(maxConcurrent, true);
    }

    /**
     * 解析文档并返回解析产物。
     *
     * @param request 文档解析请求
     * @return 文档解析结果
     */
    @Override
    public ParsedArtifact parse(DocumentParseRequest request) {
        // 1. 校验解析请求
        validateRequest(request);

        // 2. 将工作流请求转换为制品处理上下文。
        DocumentFormat format = resolveFormat(request);
        DocumentArtifactDTO artifactDTO = toArtifactDTO(request, format);

        // 3. 在并发边界内将对象存储输入流写入工作区，再复用该文件完成解析。
        return executeWithParsePermit(request.documentId(), () -> parseInWorkspace(request, format, artifactDTO));
    }

    private ParsedArtifact parseInWorkspace(DocumentParseRequest request, DocumentFormat format,
                                            DocumentArtifactDTO artifactDTO) {
        try (ArtifactWorkspace workspace = workspaceFactory.create(request.documentId());
             InputStream sourceStream = fileStorageService.load(request.originalObjectName())) {
            Path sourcePath = workspace.resolve("source" + resolveSourceExtension(format));
            boundedFileTransfer.copy(sourceStream, sourcePath, requiredWorkspaceLimit());
            return dispatchStaged(artifactDTO, new StagedDocumentBO(sourcePath, workspace));
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("暂存原始文档失败，documentId=" + request.documentId(), exception,
                    BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 按文档格式将已暂存文件交给唯一处理器。
     */
    @Override
    public ParsedArtifact parseStaged(DocumentArtifactDTO artifactDTO, StagedDocumentBO stagedDocumentBO) {
        // 1. 校验暂存文档上下文。
        if (artifactDTO == null || artifactDTO.documentId() == null || artifactDTO.format() == null) {
            throw new ServiceException("已暂存文档处理上下文不完整");
        }
        if (stagedDocumentBO == null || stagedDocumentBO.sourcePath() == null || stagedDocumentBO.workspace() == null) {
            throw new ServiceException("已暂存文档不能为空，documentId=" + artifactDTO.documentId());
        }

        return executeWithParsePermit(artifactDTO.documentId(), () -> dispatchStaged(artifactDTO, stagedDocumentBO));
    }

    /**
     * 将已暂存文件分派给格式唯一的处理器。
     */
    private ParsedArtifact dispatchStaged(DocumentArtifactDTO artifactDTO, StagedDocumentBO stagedDocumentBO) {
        // 2. 根据文档格式选择唯一处理器。
        DocumentArtifactHandler handler = handlerRegistry.requiredHandler(artifactDTO.format());
        log.info("开始执行文档制品处理，documentId={}，format={}，handlerClass={}",
                artifactDTO.documentId(), artifactDTO.format(), handler.getClass().getSimpleName());

        // 3. 执行处理并返回产物信息。
        return handler.handle(artifactDTO, stagedDocumentBO);
    }

    /**
     * 在实例级解析并发边界内执行语义化解析动作，避免 MQ 并发放大外部转换器资源消耗。
     */
    private ParsedArtifact executeWithParsePermit(Long documentId, ParseAction parseAction) {
        boolean acquired = false;
        try {
            parseSemaphore.acquire();
            acquired = true;
            return parseAction.execute();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException("等待文档解析并发许可时被中断，documentId=" + documentId,
                    exception, BaseErrorCode.SERVICE_ERROR);
        } finally {
            if (acquired) {
                parseSemaphore.release();
            }
        }
    }

    /**
     * 受并发许可保护的解析动作，避免将框架扩展点暴露为裸函数类型。
     */
    @FunctionalInterface
    private interface ParseAction {

        /** 执行一次文档解析。 */
        ParsedArtifact execute();
    }

    private void validateRequest(DocumentParseRequest request) {
        if (request == null) {
            throw new ServiceException("文档解析请求不能为空");
        }
        if (request.documentId() == null) {
            throw new ServiceException("文档ID不能为空");
        }
        if (!StringUtils.hasText(request.fileType())) {
            throw new ServiceException("文件类型不能为空，documentId=" + request.documentId());
        }
        if (!StringUtils.hasText(request.originalObjectName())) {
            throw new ServiceException("原始文件对象名不能为空，documentId=" + request.documentId());
        }
    }

    /**
     * 将工作流文件类型转换为 infra 内部格式枚举。
     */
    private DocumentFormat resolveFormat(DocumentParseRequest request) {
        try {
            DocumentFormat format = DocumentFormat.valueOf(request.fileType().trim().toUpperCase(Locale.ROOT));
            if (format == DocumentFormat.UNKNOWN) {
                throw new IllegalArgumentException("UNKNOWN");
            }
            return format;
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("不支持的文档格式，documentId=" + request.documentId()
                    + "，fileType=" + request.fileType());
        }
    }

    /**
     * 将工作流请求显式转换为制品处理传输对象。
     */
    private DocumentArtifactDTO toArtifactDTO(DocumentParseRequest request, DocumentFormat format) {
        return DocumentArtifactDTO.builder()
                .documentId(request.documentId())
                .originalFileName(request.originalFileName())
                .format(format)
                .originalObjectName(request.originalObjectName())
                .originalFileUrl(request.originalFileUrl())
                .enableOcr(request.enableOcr())
                .enableImageDescription(request.enableImageDescription())
                .build();
    }

    /**
     * 获取工作区大小限制，拒绝无效配置以避免无界落盘。
     */
    private long requiredWorkspaceLimit() {
        if (artifactProcessingProperties.getMaxWorkspaceBytes() <= 0) {
            throw new ServiceException("文档解析工作区大小限制必须大于零");
        }
        return artifactProcessingProperties.getMaxWorkspaceBytes();
    }

    /**
     * 根据格式生成固定的安全暂存扩展名。
     */
    private String resolveSourceExtension(DocumentFormat format) {
        return switch (format) {
            case PDF -> ".pdf";
            case WORD -> ".docx";
            case EXCEL -> ".xlsx";
            case PPT -> ".pptx";
            case MARKDOWN -> ".md";
            case TEXT -> ".txt";
            case UNKNOWN -> throw new ServiceException("不支持未知文档格式");
        };
    }
}
