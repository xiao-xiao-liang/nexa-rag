package com.nexarag.infra.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对象名解析器测试。
 */
class ObjectNameResolverTest {

    @Test
    void resolveOriginalObjectNameShouldKeepExtensionAndRemoveUnsafePath() {
        ObjectNameResolver resolver = new ObjectNameResolver();

        String objectName = resolver.resolveOriginalObjectName("..\\合同 版本.pdf");

        assertThat(objectName).startsWith("original/");
        assertThat(objectName).endsWith(".pdf");
        assertThat(objectName).doesNotContain("..", "\\", " ");
    }
}
