package com.nexarag.infra.parser.passthrough;

import com.nexarag.infra.parser.handler.DocumentArtifactHandler;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;
import com.nexarag.infra.constants.ParsedContentTypes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 透传文档制品处理器，用于 Markdown 和 Excel 这类无需在解析阶段转换内容的文件。
 */
@Component
@ConditionalOnProperty(prefix = "nexa.parser.passthrough", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PassthroughArtifactParser implements DocumentArtifactHandler {

    /**
     * 返回透传处理支持的格式。
     *
     * @return 支持的文件格式集合
     */
    @Override
    public Set<DocumentFormat> supportedFormats() {
        return Set.of(DocumentFormat.MARKDOWN, DocumentFormat.EXCEL);
    }

    /**
     * 返回原始文件作为解析产物。
     *
     * @param artifactDTO 文档制品处理上下文
     * @return 文档解析结果
     */
    @Override
    public ParsedArtifact handle(DocumentArtifactDTO artifactDTO, StagedDocumentBO stagedDocumentBO) {
        // 1. 根据文件类型确定透传产物内容类型
        String contentType = DocumentFormat.MARKDOWN == artifactDTO.format()
                ? ParsedContentTypes.TEXT_MARKDOWN
                : ParsedContentTypes.EXCEL;

        // 2. 复用原始文件地址作为解析产物地址
        return ParsedArtifact.builder()
                .contentType(contentType)
                .objectKey(artifactDTO.originalObjectName())
                .metadata(Map.of(
                        "parser", "passthrough",
                        "passthrough", true,
                        "originalFileName", artifactDTO.originalFileName()
                ))
                .build();
    }
}
