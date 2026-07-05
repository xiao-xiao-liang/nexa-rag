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

    @Test
    void resolveParsedObjectNameShouldUseDocumentDirectoryAndSafeExtension() {
        ObjectNameResolver resolver = new ObjectNameResolver();

        String objectName = resolver.resolveParsedObjectName(1001L, "合同.pdf", ".md");

        assertThat(objectName).isEqualTo("parsed/1001/content.md");
    }

    @Test
    void resolveParsedAssetObjectNameShouldRemoveUnsafePath() {
        ObjectNameResolver resolver = new ObjectNameResolver();

        String objectName = resolver.resolveParsedAssetObjectName(1001L, "..\\images/图 1.PNG");

        assertThat(objectName).startsWith("parsed/1001/assets/");
        assertThat(objectName).endsWith(".png");
        assertThat(objectName).doesNotContain("..", "\\", " ");
    }
}
