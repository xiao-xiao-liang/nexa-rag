package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.model.bo.split.ChunkDraft;
import com.nexarag.document.model.bo.split.DocumentSplitResult;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void toChunkShouldRejectBlankIndexContent() throws Exception {
        DocumentChunkServiceImpl service = new DocumentChunkServiceImpl();
        var method = DocumentChunkServiceImpl.class.getDeclaredMethod("toChunk", Long.class, ChunkDraft.class, int.class);
        method.setAccessible(true);
        ChunkDraft draft = new ChunkDraft("chunk_1", null, 11L, "正文", " ", 1, Map.of(), false);

        assertThatThrownBy(() -> method.invoke(service, 1L, draft, 0))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("文档片段索引内容不能为空，documentId=1");
    }

    @Test
    void toChunkShouldRejectNullIndexContentFromFullConstructor() throws Exception {
        DocumentChunkServiceImpl service = new DocumentChunkServiceImpl();
        var method = DocumentChunkServiceImpl.class.getDeclaredMethod("toChunk", Long.class, ChunkDraft.class, int.class);
        method.setAccessible(true);
        ChunkDraft draft = new ChunkDraft("chunk_1", null, 11L, "正文", null, 1, Map.of(), false);

        assertThatThrownBy(() -> method.invoke(service, 1L, draft, 0))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("文档片段索引内容不能为空，documentId=1");
    }

    @Test
    void toChunkShouldKeepSectionAndIndexContent() throws Exception {
        DocumentChunkServiceImpl service = new DocumentChunkServiceImpl();
        var method = DocumentChunkServiceImpl.class.getDeclaredMethod("toChunk", Long.class, ChunkDraft.class, int.class);
        method.setAccessible(true);
        ChunkDraft draft = new ChunkDraft("chunk_1", "parent_1", 11L, "原始正文", "标题 > 原始正文", 1, Map.of(), false);

        DocumentChunk result = (DocumentChunk) method.invoke(service, 1L, draft, 0);

        assertThat(result.getSectionId()).isEqualTo(11L);
        assertThat(result.getText()).isEqualTo("原始正文");
        assertThat(result.getIndexContent()).isEqualTo("标题 > 原始正文");
    }

    @Test
    void legacyAndUnstructuredDraftsShouldExplicitlyKeepTextAsIndexContent() {
        ChunkDraft legacyDraft = new ChunkDraft("chunk_1", null, "原始正文", 1, Map.of(), false);
        ChunkDraft fullDraft = new ChunkDraft("chunk_2", null, 11L, "非结构化正文", "标题 > 非结构化正文", 1, Map.of(), false);

        DocumentSplitResult splitResult = DocumentSplitResult.unstructured(List.of(fullDraft));

        assertThat(legacyDraft.indexContent()).isEqualTo("原始正文");
        assertThat(splitResult.chunks().getFirst().indexContent()).isEqualTo("非结构化正文");
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
