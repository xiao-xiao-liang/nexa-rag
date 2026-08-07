package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexarag.document.entity.DocumentChunk;
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

    @Test
    void pageByDocumentIdShouldNormalizePageArguments() {
        TestableDocumentChunkServiceImpl service = new TestableDocumentChunkServiceImpl();
        Page<DocumentChunk> page = Page.of(1, 100);
        page.setRecords(List.of());
        service.chunkPage = page;

        IPage<DocumentChunk> result = service.pageByDocumentId(1L, 0, 1000);

        assertThat(result).isSameAs(page);
        assertThat(service.queriedDocumentId).isEqualTo(1L);
        assertThat(service.queriedPageNum).isEqualTo(1);
        assertThat(service.queriedPageSize).isEqualTo(100);
    }

    private static final class TestableDocumentChunkServiceImpl extends DocumentChunkServiceImpl {

        private Long queriedDocumentId;
        private long queriedPageNum;
        private long queriedPageSize;
        private IPage<DocumentChunk> chunkPage;

        @Override
        protected IPage<DocumentChunk> queryChunkPage(Long documentId, long pageNum, long pageSize) {
            this.queriedDocumentId = documentId;
            this.queriedPageNum = pageNum;
            this.queriedPageSize = pageSize;
            return chunkPage;
        }
    }
}
