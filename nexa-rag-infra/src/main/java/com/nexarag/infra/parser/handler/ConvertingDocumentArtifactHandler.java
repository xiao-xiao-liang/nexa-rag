package com.nexarag.infra.parser.handler;

import com.nexarag.infra.parser.converter.DocumentConverter;
import com.nexarag.infra.parser.converter.DocumentConverterRegistry;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ExtractedDocumentBO;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;
import com.nexarag.infra.parser.publish.ArtifactPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 转换型文档制品处理器，将已暂存的原始文件交由格式转换器处理。
 */
@Component
@RequiredArgsConstructor
public class ConvertingDocumentArtifactHandler implements DocumentArtifactHandler {

    private final DocumentConverterRegistry converterRegistry;
    private final ArtifactPublisher artifactPublisher;

    /**
     * 当前阶段由 Pandoc 负责的格式。
     *
     * @return 支持的文件格式集合
     */
    @Override
    public Set<DocumentFormat> supportedFormats() {
        return Set.of(DocumentFormat.WORD, DocumentFormat.PDF);
    }

    /**
     * 执行格式转换并发布最终制品。
     *
     * @param artifactDTO 文档处理上下文
     * @param stagedDocumentBO 已暂存原始文档
     * @return 已发布的解析制品
     */
    @Override
    public ParsedArtifact handle(DocumentArtifactDTO artifactDTO, StagedDocumentBO stagedDocumentBO) {
        // 1. 调用格式对应的唯一转换器。
        DocumentConverter converter = converterRegistry.requiredConverter(artifactDTO.format());
        ExtractedDocumentBO documentBO = converter.convert(artifactDTO, stagedDocumentBO.sourcePath(),
                stagedDocumentBO.workspace());

        // 2. 发布转换结果。
        return artifactPublisher.publish(artifactDTO, documentBO);
    }
}
