package com.nexarag.infra.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档解析结果测试。
 */
class DocumentParseResultTest {

    @Test
    void markdownResultShouldExposeContentAndUrl() {
        DocumentParseResult result = new DocumentParseResult("markdown", "# 标题", "minio://parsed/doc.md");

        assertThat(result.contentType()).isEqualTo("markdown");
        assertThat(result.content()).isEqualTo("# 标题");
        assertThat(result.parsedFileUrl()).isEqualTo("minio://parsed/doc.md");
    }
}
