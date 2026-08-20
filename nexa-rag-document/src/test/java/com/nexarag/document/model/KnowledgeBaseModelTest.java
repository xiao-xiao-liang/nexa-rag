package com.nexarag.document.model;

import com.nexarag.document.model.entity.Document;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 知识库领域模型测试。
 */
class KnowledgeBaseModelTest {

    @Test
    void modelShouldExposeKnowledgeBaseDataObjectAndDocumentMembership() {
        Class<?> knowledgeBaseClass = loadClass("com.nexarag.document.model.dataobject.KnowledgeBaseDO");
        Class<?> createDtoClass = loadClass("com.nexarag.document.model.dto.CreateKnowledgeBaseDTO");
        Class<?> updateDtoClass = loadClass("com.nexarag.document.model.dto.UpdateKnowledgeBaseDTO");
        Class<?> statisticsVoClass = loadClass("com.nexarag.document.model.vo.KnowledgeBaseStatisticsVO");
        Class<?> summaryVoClass = loadClass("com.nexarag.document.model.vo.KnowledgeBaseSummaryVO");
        Class<?> detailVoClass = loadClass("com.nexarag.document.model.vo.KnowledgeBaseDetailVO");

        assertThat(knowledgeBaseClass).isNotNull();
        assertThat(createDtoClass).isNotNull();
        assertThat(updateDtoClass).isNotNull();
        assertThat(statisticsVoClass).isNotNull();
        assertThat(summaryVoClass).isNotNull();
        assertThat(detailVoClass).isNotNull();
        assertThat(findField(knowledgeBaseClass, "isDefault").getType()).isEqualTo(Integer.class);
        assertThat(findField(Document.class, "knowledgeBaseId").getType()).isEqualTo(Long.class);
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    private Field findField(Class<?> type, String fieldName) {
        try {
            return type.getDeclaredField(fieldName);
        } catch (NoSuchFieldException exception) {
            throw new AssertionError("缺少字段：" + fieldName, exception);
        }
    }
}
