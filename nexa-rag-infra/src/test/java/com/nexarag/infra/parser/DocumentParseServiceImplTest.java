package com.nexarag.infra.parser;

import com.nexarag.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档解析服务测试，验证解析器按文件类型分派。
 */
class DocumentParseServiceImplTest {

    @Test
    void parseShouldUseSupportedParser() {
        RecordingParser parser = new RecordingParser(ParserFileTypes.PDF);
        DocumentParseServiceImpl service = new DocumentParseServiceImpl(List.of(parser));

        DocumentParseResult result = service.parse(request(ParserFileTypes.PDF));

        assertThat(parser.parsed).isTrue();
        assertThat(result.parsedFileUrl()).isEqualTo("http://127.0.0.1:9000/nexa-rag/parsed/1/content.md");
    }

    @Test
    void parseShouldFailWhenNoParserSupportsFileType() {
        DocumentParseServiceImpl service = new DocumentParseServiceImpl(List.of(new RecordingParser(ParserFileTypes.PDF)));

        assertThatThrownBy(() -> service.parse(request(ParserFileTypes.UNKNOWN)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未找到可用文档解析器");
    }

    private DocumentParseRequest request(String fileType) {
        return DocumentParseRequest.builder()
                .documentId(1L)
                .originalFileName("demo.pdf")
                .fileType(fileType)
                .originalObjectName("original/demo.pdf")
                .originalFileUrl("http://127.0.0.1:9000/nexa-rag/original/demo.pdf")
                .enableOcr(true)
                .enableImageDescription(false)
                .build();
    }

    private static class RecordingParser implements DocumentParser {

        private final String supportedFileType;
        private boolean parsed;

        private RecordingParser(String supportedFileType) {
            this.supportedFileType = supportedFileType;
        }

        @Override
        public boolean supports(DocumentParseRequest request) {
            return supportedFileType.equals(request.fileType());
        }

        @Override
        public DocumentParseResult parse(DocumentParseRequest request) {
            parsed = true;
            return DocumentParseResult.builder()
                    .contentType(ParsedContentTypes.TEXT_MARKDOWN)
                    .parsedObjectName("parsed/1/content.md")
                    .parsedFileUrl("http://127.0.0.1:9000/nexa-rag/parsed/1/content.md")
                    .metadata(Map.of("parser", supportedFileType))
                    .build();
        }
    }
}
