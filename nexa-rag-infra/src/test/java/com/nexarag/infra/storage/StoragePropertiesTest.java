package com.nexarag.infra.storage;

import com.nexarag.infra.config.StorageProperties;
import com.nexarag.infra.enums.StorageType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 存储通用配置测试。
 */
class StoragePropertiesTest {

    @Test
    void shouldUseMinioAsDefaultStorageTypeAndLocalEndpoint() {
        StorageProperties properties = new StorageProperties();

        assertThat(properties.getType()).isEqualTo(StorageType.MINIO);
        assertThat(properties.getEndpoint()).isEqualTo("http://127.0.0.1:9000");
        assertThat(properties.getAccessKey()).isEqualTo("");
        assertThat(properties.getSecretKey()).isEqualTo("");
        assertThat(properties.getBucket()).isEqualTo("nexa-rag");
        assertThat(properties.isCreateBucket()).isTrue();
    }
}
