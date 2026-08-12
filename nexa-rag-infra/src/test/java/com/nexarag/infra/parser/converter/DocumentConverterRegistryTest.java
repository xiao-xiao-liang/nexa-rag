package com.nexarag.infra.parser.converter;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ExtractedDocumentBO;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档转换器注册表测试。
 */
class DocumentConverterRegistryTest {

    @Test
    void constructorShouldRejectDuplicateConverterFormat() {
        DocumentConverter firstConverter = converter(DocumentFormat.WORD);
        DocumentConverter secondConverter = converter(DocumentFormat.WORD);

        assertThatThrownBy(() -> new DocumentConverterRegistry(List.of(firstConverter, secondConverter)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("重复注册文档转换器")
                .hasMessageContaining("WORD");
    }

    @Test
    void requiredConverterShouldReturnConverterForRegisteredFormat() {
        DocumentConverter converter = converter(DocumentFormat.PDF);
        DocumentConverterRegistry registry = new DocumentConverterRegistry(List.of(converter));

        assertThat(registry.requiredConverter(DocumentFormat.PDF)).isSameAs(converter);
    }

    private DocumentConverter converter(DocumentFormat format) {
        return new DocumentConverter() {
            @Override
            public Set<DocumentFormat> supportedFormats() {
                return Set.of(format);
            }

            @Override
            public ExtractedDocumentBO convert(DocumentArtifactDTO artifactDTO, Path stagedSource,
                                               ArtifactWorkspace workspace) {
                throw new UnsupportedOperationException("测试转换器不应执行转换");
            }
        };
    }
}
