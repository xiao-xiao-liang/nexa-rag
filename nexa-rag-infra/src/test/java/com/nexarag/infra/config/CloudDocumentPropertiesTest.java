package com.nexarag.infra.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 云文档配置默认值测试。
 */
class CloudDocumentPropertiesTest {

    @Test
    void feishuBlockReadShouldUseBoundedDefaults() {
        CloudDocumentProperties properties = new CloudDocumentProperties();

        CloudDocumentProperties.FeishuProperties.BlockReadProperties blockRead =
                properties.getFeishu().getBlockRead();

        assertThat(blockRead.isEnabled()).isTrue();
        assertThat(blockRead.getPageSize()).isEqualTo(100);
        assertThat(blockRead.getMaxBlockCount()).isEqualTo(2_000);
        assertThat(blockRead.getMaxJsonBytes()).isEqualTo(8L * 1024 * 1024);
    }
}
