package com.nexarag.infra.messaging.document.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档流水线消息测试，验证消息关键字段的边界约束。
 */
class DocumentPipelineMessageTest {

    private static final LocalDateTime CREATED_TIME = LocalDateTime.of(2026, 7, 11, 10, 0);

    @Test
    void shouldRejectNonPositiveDocumentId() {
        assertThatThrownBy(() -> new DocumentPipelineMessage(0L, 2L, "process-001", null, 2, CREATED_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文档ID必须大于0");
    }

    @Test
    void shouldRejectMissingDocumentVersionId() {
        assertThatThrownBy(() -> new DocumentPipelineMessage(1L, null, "process-001", 1L, 2, CREATED_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文档版本ID必须大于0");
    }

    @Test
    void shouldRejectLegacySchemaMessageWithoutDocumentVersionId() {
        assertThatThrownBy(() -> new DocumentPipelineMessage(1L, null, "process-001", 1L, 1, CREATED_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文档版本ID必须大于0");
    }

    @Test
    void shouldRejectBlankProcessId() {
        assertThatThrownBy(() -> new DocumentPipelineMessage(1L, 2L, " ", null, 2, CREATED_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("处理批次ID不能为空");
    }

    @Test
    void shouldRejectNonPositiveSchemaVersion() {
        assertThatThrownBy(() -> new DocumentPipelineMessage(1L, 2L, "process-001", null, 0, CREATED_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("消息结构版本必须大于0");
    }

    @Test
    void shouldRejectNullCreatedTime() {
        assertThatThrownBy(() -> new DocumentPipelineMessage(1L, 2L, "process-001", null, 2, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("消息创建时间不能为空");
    }
}
