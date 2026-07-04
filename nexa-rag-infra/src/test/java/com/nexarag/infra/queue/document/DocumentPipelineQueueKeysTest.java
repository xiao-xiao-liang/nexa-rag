package com.nexarag.infra.queue.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档流水线 Redis Key 生成器测试。
 */
class DocumentPipelineQueueKeysTest {

    @Test
    void shouldGenerateDefaultDocumentPipelineKeys() {
        DocumentPipelineQueueProperties properties = new DocumentPipelineQueueProperties();
        DocumentPipelineQueueKeys keys = new DocumentPipelineQueueKeys(properties);

        assertThat(keys.waitingKey()).isEqualTo("nexa:document:pipeline:waiting");
        assertThat(keys.runningKey()).isEqualTo("nexa:document:pipeline:running");
        assertThat(keys.leaseKey(1L)).isEqualTo("nexa:document:pipeline:lease:1");
        assertThat(keys.metaKey(1L)).isEqualTo("nexa:document:pipeline:meta:1");
        assertThat(keys.retryKey(1L)).isEqualTo("nexa:document:pipeline:retry:1");
        assertThat(keys.sequenceKey()).isEqualTo("nexa:document:pipeline:sequence");
    }

    @Test
    void shouldUseCustomKeyPrefix() {
        DocumentPipelineQueueProperties properties = new DocumentPipelineQueueProperties();
        properties.setKeyPrefix("nexa:test:pipeline");
        DocumentPipelineQueueKeys keys = new DocumentPipelineQueueKeys(properties);

        assertThat(keys.waitingKey()).isEqualTo("nexa:test:pipeline:waiting");
        assertThat(keys.leaseKey(99L)).isEqualTo("nexa:test:pipeline:lease:99");
    }
}