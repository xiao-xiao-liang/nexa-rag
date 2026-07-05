package com.nexarag.infra.parser;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档解析结果测试。
 */
class DocumentParseResultTest {

    @Test
    void markdownResultShouldExposeContentUrlAndMetadata() {
        DocumentParseResult result = DocumentParseResult.builder()
                .contentType(ParsedContentTypes.TEXT_MARKDOWN)
                .content("# 标题")
                .parsedObjectName("parsed/1/content.md")
                .parsedFileUrl("http://127.0.0.1:9000/nexa-rag/parsed/1/content.md")
                .metadata(Map.of("parser", "mineru"))
                .build();

        assertThat(result.contentType()).isEqualTo(ParsedContentTypes.TEXT_MARKDOWN);
        assertThat(result.content()).isEqualTo("# 标题");
        assertThat(result.parsedObjectName()).isEqualTo("parsed/1/content.md");
        assertThat(result.parsedFileUrl()).isEqualTo("http://127.0.0.1:9000/nexa-rag/parsed/1/content.md");
        assertThat(result.metadata()).containsEntry("parser", "mineru");
    }
}
