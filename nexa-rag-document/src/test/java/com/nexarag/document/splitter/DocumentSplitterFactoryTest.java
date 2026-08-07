package com.nexarag.document.splitter;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.enums.DocumentErrorCode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档切分器工厂测试。
 */
class DocumentSplitterFactoryTest {

    @Test
    void shouldReturnSplitterByStrategy() {
        // 1. 准备指定策略的测试切分器
        DocumentSplitter splitter = new TestDocumentSplitter(SplitStrategy.PARENT_MARKDOWN);
        DocumentSplitterFactory factory = new DocumentSplitterFactory(List.of(splitter));

        // 2. 根据策略获取切分器并校验返回同一个实例
        DocumentSplitter result = factory.getRequired(SplitStrategy.PARENT_MARKDOWN);

        assertThat(result).isSameAs(splitter);
        DocumentSplitResult splitResult = result.split(null);
        assertThat(splitResult.sections()).isEmpty();
        assertThat(splitResult.structured()).isFalse();
        assertThat(splitResult.chunks()).allSatisfy(chunk -> {
            assertThat(chunk.sectionId()).isNull();
            assertThat(chunk.indexContent()).isEqualTo(chunk.text());
        });
    }

    @Test
    void shouldThrowServiceExceptionWhenSplitterMissing() {
        // 1. 准备没有任何切分器的工厂
        DocumentSplitterFactory factory = new DocumentSplitterFactory(List.of());

        // 2. 校验缺少策略实现时抛出文档处理配置错误
        assertThatThrownBy(() -> factory.getRequired(SplitStrategy.EXCEL))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID.code());
    }

    @Test
    void unstructuredResultShouldClearSectionAndUseTextAsIndexContent() {
        ChunkDraft chunk = new ChunkDraft("chunk_1", "parent_1", 10L, "正文", "自定义索引内容", 2, Map.of(), false);

        DocumentSplitResult splitResult = DocumentSplitResult.unstructured(List.of(chunk));

        assertThat(splitResult.chunks()).singleElement().satisfies(resultChunk -> {
            assertThat(resultChunk.sectionId()).isNull();
            assertThat(resultChunk.indexContent()).isEqualTo(resultChunk.text());
            assertThat(resultChunk.chunkId()).isEqualTo("chunk_1");
            assertThat(resultChunk.parentChunkId()).isEqualTo("parent_1");
            assertThat(resultChunk.tokenCount()).isEqualTo(2);
            assertThat(resultChunk.metadata()).isEmpty();
            assertThat(resultChunk.skipIndex()).isFalse();
        });
    }

    @Test
    void chunkDraftShouldSnapshotMetadataAndExposeItAsUnmodifiable() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("nullable", null);
        ChunkDraft chunk = new ChunkDraft("正文", metadata, false);

        metadata.put("later", "不应透传");

        assertThat(chunk.metadata()).containsEntry("nullable", null).doesNotContainKey("later");
        assertThatThrownBy(() -> chunk.metadata().put("forbidden", "不允许修改"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 测试用文档切分器。
     *
     * @param strategy 切分策略
     */
    private record TestDocumentSplitter(SplitStrategy strategy) implements DocumentSplitter {

        @Override
        public DocumentSplitResult split(DocumentSplitContext context) {
            // 1. 返回最小片段草稿用于满足接口契约
            return DocumentSplitResult.unstructured(List.of(new ChunkDraft("测试内容", Map.of(), false)));
        }
    }
}
