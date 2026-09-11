package com.nexarag.infra.parser.passthrough;

import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;
import com.nexarag.infra.constants.ParsedContentTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 透传文档制品处理器测试。
 */
class PassthroughArtifactParserTest {

    @Test
    void supportedFormatsShouldContainExcelOnly() {
        PassthroughArtifactParser parser = new PassthroughArtifactParser();

        assertThat(parser.supportedFormats()).containsExactly(DocumentFormat.EXCEL);
    }

    @Test
    void handleShouldReturnOriginalFileAsParsedFile() {
        PassthroughArtifactParser parser = new PassthroughArtifactParser();

        ParsedArtifact result = parser.handle(artifact(DocumentFormat.EXCEL), mock(StagedDocumentBO.class));

        assertThat(result.objectKey()).isEqualTo("original/demo.xlsx");
        assertThat(result.contentType()).isEqualTo(ParsedContentTypes.EXCEL);
        assertThat(result.metadata()).containsEntry("passthrough", true);
    }

    private DocumentArtifactDTO artifact(DocumentFormat format) {
        return DocumentArtifactDTO.builder()
                .documentId(1L)
                .format(format)
                .originalFileName("demo.xlsx")
                .originalObjectName("original/demo.xlsx")
                .originalFileUrl("http://127.0.0.1:9000/nexa-rag/original/demo.xlsx")
                .build();
    }
}
