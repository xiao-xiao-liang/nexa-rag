package com.nexarag.infra.parser.converter;

import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ExtractedDocumentBO;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;

import java.nio.file.Path;
import java.util.Set;

/**
 * 文档格式转换 SPI，仅负责将已阶段化的文件转换为工作区内的制品文件。
 */
public interface DocumentConverter {

    /**
     * 返回当前转换器支持的全部文件格式。
     *
     * @return 支持的文件格式集合
     */
    Set<DocumentFormat> supportedFormats();

    /**
     * 将已阶段化的原始文件转换为 Markdown 与资源文件。
     *
     * @param artifactDTO  文档处理上下文
     * @param stagedSource 工作区内的原始文件
     * @param workspace    当前解析工作区
     * @return 文件化转换结果
     */
    ExtractedDocumentBO convert(DocumentArtifactDTO artifactDTO, Path stagedSource, ArtifactWorkspace workspace);
}
