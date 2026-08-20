package com.nexarag.document.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 知识库服务实现契约测试。
 */
class KnowledgeBaseServiceImplementationContractTest {

    @Test
    void serviceImplementationShouldBeAvailable() {
        assertThat(loadClass("com.nexarag.document.service.impl.KnowledgeBaseServiceImpl"))
                .isNotNull();
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }
}
