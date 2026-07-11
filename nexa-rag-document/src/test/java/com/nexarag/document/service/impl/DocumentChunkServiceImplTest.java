package com.nexarag.document.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档片段服务实现测试。
 */
class DocumentChunkServiceImplTest {

    @Test
    void replaceDocumentChunksShouldRollbackWhenAnyStepFails() throws NoSuchMethodException {
        Transactional transactional = DocumentChunkServiceImpl.class
                .getMethod("replaceDocumentChunks", Long.class, List.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(Arrays.asList(transactional.rollbackFor())).contains(Exception.class);
    }
}
