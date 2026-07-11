package com.nexarag.infra.messaging.document.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档流水线发布结果测试，验证成功结果只能携带有效消息ID。
 */
class DocumentPipelinePublishResultTest {

    @Test
    void shouldCreateSuccessResultWithMessageId() {
        DocumentPipelinePublishResult result = DocumentPipelinePublishResult.success("message-001");

        assertThat(result.success()).isTrue();
        assertThat(result.messageId()).isEqualTo("message-001");
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void shouldRejectBlankMessageId() {
        assertThatThrownBy(() -> DocumentPipelinePublishResult.success(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("消息ID不能为空");
    }
}
