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
    void supportedFormatsShouldContainMarkdownAndExcelOnly() {
        PassthroughArtifactParser parser = new PassthroughArtifactParser();

        assertThat(parser.supportedFormats()).containsExactlyInAnyOrder(DocumentFormat.MARKDOWN, DocumentFormat.EXCEL);
    }

    @Test
    void handleShouldReturnOriginalFileAsParsedFile() {
        PassthroughArtifactParser parser = new PassthroughArtifactParser();

        ParsedArtifact result = parser.handle(artifact(DocumentFormat.MARKDOWN), mock(StagedDocumentBO.class));

        assertThat(result.objectKey()).isEqualTo("original/demo.md");
        assertThat(result.contentType()).isEqualTo(ParsedContentTypes.TEXT_MARKDOWN);
        assertThat(result.metadata()).containsEntry("passthrough", true);
    }

    private DocumentArtifactDTO artifact(DocumentFormat format) {
        return DocumentArtifactDTO.builder()
                .documentId(1L)
                .format(format)
                .originalFileName("demo.md")
                .originalObjectName("original/demo.md")
                .originalFileUrl("http://127.0.0.1:9000/nexa-rag/original/demo.md")
                .build();
    }
}
