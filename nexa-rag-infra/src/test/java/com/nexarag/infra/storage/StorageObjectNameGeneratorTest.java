package com.nexarag.infra.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 存储对象名生成器测试。
 */
class StorageObjectNameGeneratorTest {

    @Test
    void generateOriginalObjectNameShouldKeepExtensionAndRemoveUnsafePath() {
        StorageObjectNameGenerator generator = new StorageObjectNameGenerator();

        String objectName = generator.generateOriginalObjectName("..\\合同 版本.pdf");

        assertThat(objectName).startsWith("original/");
        assertThat(objectName).endsWith(".pdf");
        assertThat(objectName).doesNotContain("..", "\\", " ");
    }
}
