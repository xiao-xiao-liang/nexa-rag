package com.nexarag.document.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 知识库嵌套文档接口契约测试。
 */
class KnowledgeBaseApiContractTest {

    @Test
    void controllersShouldExposeKnowledgeBaseScopedApi() {
        Class<?> knowledgeBaseController = loadClass("com.nexarag.document.controller.KnowledgeBaseController");

        assertThat(knowledgeBaseController).isNotNull();
        assertThat(knowledgeBaseController.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/knowledge-bases");
        assertThat(DocumentController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/knowledge-bases/{knowledgeBaseId}/documents");
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }
}
