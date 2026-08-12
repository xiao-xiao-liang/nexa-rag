package com.nexarag.infra.parser.workspace;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.ArtifactProcessingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 文档解析工作区工厂。 */
@Component
@RequiredArgsConstructor
public class ArtifactWorkspaceFactory {
    private final ArtifactProcessingProperties properties;

    /** 创建指定文档的隔离工作区。 */
    public ArtifactWorkspace create(Long documentId) {
        if (documentId == null) { throw new ServiceException("文档ID不能为空"); }
        try {
            Path tempRoot = properties.getTempRoot().toAbsolutePath().normalize();
            Files.createDirectories(tempRoot);
            return new ArtifactWorkspace(Files.createTempDirectory(tempRoot, "document-" + documentId + "-"), tempRoot);
        } catch (IOException exception) {
            throw new ServiceException("创建文档解析工作区失败，documentId=" + documentId,
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
