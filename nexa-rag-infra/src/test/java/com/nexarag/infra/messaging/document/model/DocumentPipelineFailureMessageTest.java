package com.nexarag.infra.messaging.document.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档流水线失败消息测试。
 */
class DocumentPipelineFailureMessageTest {

    @Test
    void shouldKeepDocumentVersionInFailureBoundary() {
        DocumentPipelineFailureMessage message = new DocumentPipelineFailureMessage(
                101L, 1L, 2L, "process-1", "INDEXING", "索引失败", "detail",
                6, "message-1", LocalDateTime.now());

        assertThat(message.documentVersionId()).isEqualTo(2L);
    }
}
