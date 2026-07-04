package com.nexarag.infra.storage;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.StorageProperties;
import com.nexarag.infra.enums.StorageType;
import com.nexarag.infra.storage.service.FileStorageServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 委派文件存储服务测试。
 */
class FileStorageServiceImplTest {

    @Test
    void saveShouldDelegateToConfiguredStrategy() {
        StorageProperties properties = new StorageProperties();
        properties.setType(StorageType.MINIO);
        RecordingStorageStrategy strategy = new RecordingStorageStrategy(StorageType.MINIO);
        FileStorageServiceImpl storageService = new FileStorageServiceImpl(properties, List.of(strategy));

        StoredFile storedFile = storageService.save("demo.pdf", InputStream.nullInputStream(), 10L);

        assertThat(strategy.savedFileName).isEqualTo("demo.pdf");
        assertThat(strategy.savedSize).isEqualTo(10L);
        assertThat(storedFile.objectName()).isEqualTo("original/demo.pdf");
    }

    @Test
    void saveShouldFailWhenConfiguredStrategyMissing() {
        StorageProperties properties = new StorageProperties();
        properties.setType(StorageType.OSS);
        FileStorageServiceImpl storageService = new FileStorageServiceImpl(properties,
                List.of(new RecordingStorageStrategy(StorageType.MINIO)));

        assertThatThrownBy(() -> storageService.save("demo.pdf", InputStream.nullInputStream(), 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未找到文件存储策略");
    }

    private static class RecordingStorageStrategy implements FileStorageStrategy {

        private final StorageType storageType;
        private String savedFileName;
        private long savedSize;

        private RecordingStorageStrategy(StorageType storageType) {
            this.storageType = storageType;
        }

        @Override
        public StorageType storageType() {
            return storageType;
        }

        @Override
        public StoredFile save(String fileName, InputStream inputStream, long size) {
            this.savedFileName = fileName;
            this.savedSize = size;
            return new StoredFile("original/demo.pdf", "http://127.0.0.1:9000/nexa-rag/original/demo.pdf", size);
        }

        @Override
        public InputStream load(String objectName) {
            return InputStream.nullInputStream();
        }

        @Override
        public void delete(String objectName) {
        }
    }
}
