package com.nexarag.infra.parser.passthrough;

import com.nexarag.infra.parser.model.DocumentParseRequest;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.constants.ParsedContentTypes;
import com.nexarag.infra.constants.ParserFileTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 透传文档解析器测试。
 */
class PassthroughArtifactParserTest {

    @Test
    void supportsShouldAcceptMarkdownAndExcel() {
        PassthroughArtifactParser parser = new PassthroughArtifactParser();

        assertThat(parser.supports(request(ParserFileTypes.MARKDOWN))).isTrue();
        assertThat(parser.supports(request(ParserFileTypes.EXCEL))).isTrue();
        assertThat(parser.supports(request(ParserFileTypes.PDF))).isFalse();
    }

    @Test
    void parseShouldReturnOriginalFileAsParsedFile() {
        PassthroughArtifactParser parser = new PassthroughArtifactParser();

        ParsedArtifact result = parser.parse(request(ParserFileTypes.MARKDOWN));

        assertThat(result.objectKey()).isEqualTo("original/demo.md");
        assertThat(result.contentType()).isEqualTo(ParsedContentTypes.TEXT_MARKDOWN);
        assertThat(result.metadata()).containsEntry("passthrough", true);
    }

    private DocumentParseRequest request(String fileType) {
        return DocumentParseRequest.builder()
                .documentId(1L)
                .fileType(fileType)
                .originalFileName("demo.md")
                .originalObjectName("original/demo.md")
                .originalFileUrl("http://127.0.0.1:9000/nexa-rag/original/demo.md")
                .build();
    }
}
