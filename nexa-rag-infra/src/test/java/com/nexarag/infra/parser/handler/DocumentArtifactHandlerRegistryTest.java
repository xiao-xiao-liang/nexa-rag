package com.nexarag.infra.parser.handler;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档制品处理器注册表测试。
 */
class DocumentArtifactHandlerRegistryTest {

    @Test
    void requiredHandlerShouldRejectUnsupportedFormat() {
        DocumentArtifactHandlerRegistry registry = new DocumentArtifactHandlerRegistry(List.of());

        assertThatThrownBy(() -> registry.requiredHandler(DocumentFormat.UNKNOWN))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未找到文档制品处理器");
    }

    @Test
    void requiredHandlerShouldReturnRegisteredHandler() {
        DocumentArtifactHandler handler = new DocumentArtifactHandler() {
            @Override
            public Set<DocumentFormat> supportedFormats() {
                return Set.of(DocumentFormat.MARKDOWN);
            }

            @Override
            public ParsedArtifact handle(DocumentArtifactDTO artifactDTO, StagedDocumentBO stagedDocumentBO) {
                throw new UnsupportedOperationException("测试处理器不应执行处理");
            }
        };
        DocumentArtifactHandlerRegistry registry = new DocumentArtifactHandlerRegistry(List.of(handler));

        assertThat(registry.requiredHandler(DocumentFormat.MARKDOWN)).isSameAs(handler);
    }
}
