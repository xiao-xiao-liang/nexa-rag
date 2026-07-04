package com.nexarag.infra.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MinIO 存储配置测试。
 */
class MinioFileStoragePropertiesTest {

    @Test
    void shouldUseLocalMinioDefaults() {
        MinioFileStorageProperties properties = new MinioFileStorageProperties();

        assertThat(properties.getEndpoint()).isEqualTo("http://127.0.0.1:9000");
        assertThat(properties.getAccessKey()).isEqualTo("");
        assertThat(properties.getSecretKey()).isEqualTo("");
        assertThat(properties.getBucket()).isEqualTo("nexa-rag");
        assertThat(properties.isCreateBucket()).isTrue();
    }
}
