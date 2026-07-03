package com.nexarag.document.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档状态流转测试。
 */
class DocumentStatusTest {

    @Test
    void queuedShouldAllowParsing() {
        assertThat(DocumentStatus.QUEUED.canTransferTo(DocumentStatus.PARSING)).isTrue();
    }

    @Test
    void indexedShouldNotAllowParsingDirectly() {
        assertThat(DocumentStatus.INDEXED.canTransferTo(DocumentStatus.PARSING)).isFalse();
    }
}
