package com.nexarag.infra.parser.handler;

import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;

import java.util.Set;

/**
 * 文档制品处理策略，负责将指定文件格式处理为后续工作流可读取的制品。
 */
public interface DocumentArtifactHandler {

    /**
     * 返回当前处理策略支持的文件格式。
     *
     * @return 支持的文件格式集合
     */
    Set<DocumentFormat> supportedFormats();

    /**
     * 处理已经暂存到受管工作区的文档，并返回已发布的解析制品。
     *
     * @param artifactDTO 文档制品处理请求
     * @param stagedDocumentBO 已暂存原始文档
     * @return 已发布的解析制品
     */
    ParsedArtifact handle(DocumentArtifactDTO artifactDTO, StagedDocumentBO stagedDocumentBO);
}
