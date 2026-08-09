package com.nexarag.document.splitter;

import com.nexarag.document.toolkit.DocumentChunkIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocumentChunkIdGenerator 单元测试。
 */
class DocumentChunkIdGeneratorTest {

    @Test
    void nextChunkIdShouldReturnStandardUuidUsedBySpringAiDocument() {
        DocumentChunkIdGenerator generator = new DocumentChunkIdGenerator();

        String chunkId = generator.nextChunkId(101L);

        assertThat(chunkId).hasSize(36);
        assertThat(UUID.fromString(chunkId).toString()).isEqualTo(chunkId);
    }
}
