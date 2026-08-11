package com.nexarag.infra.parser;

import com.nexarag.infra.constants.ParsedContentTypes;
import com.nexarag.infra.parser.model.ParsedArtifact;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 解析产物测试，验证跨阶段仅交接对象存储快照定位信息。
 */
class ParsedArtifactTest {

    @Test
    void artifactShouldExposeObjectKeyContentTypeAndMetadata() {
        ParsedArtifact artifact = ParsedArtifact.builder()
                .objectKey("parsed/1/content.md")
                .contentType(ParsedContentTypes.TEXT_MARKDOWN)
                .metadata(Map.of("parser", "mineru"))
                .build();

        assertThat(artifact.objectKey()).isEqualTo("parsed/1/content.md");
        assertThat(artifact.contentType()).isEqualTo(ParsedContentTypes.TEXT_MARKDOWN);
        assertThat(artifact.metadata()).containsEntry("parser", "mineru");
    }
}
