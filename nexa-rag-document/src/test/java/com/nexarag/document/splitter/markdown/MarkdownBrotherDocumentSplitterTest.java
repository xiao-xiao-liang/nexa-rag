package com.nexarag.document.splitter.markdown;

import com.nexarag.document.model.dto.MarkdownSplitOptions;
import com.nexarag.document.model.dto.SplitConfigRequest;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.splitter.ChunkDraft;
import com.nexarag.document.splitter.DocumentChunkIdGenerator;
import com.nexarag.document.splitter.DocumentSplitContext;
import com.nexarag.document.splitter.DocumentSplitResult;
import com.nexarag.document.splitter.DocumentSectionIdGenerator;
import com.nexarag.document.splitter.support.TextWindowSplitter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Markdown 同级切分器测试。
 */
class MarkdownBrotherDocumentSplitterTest {

    @Test
    void splitShouldCreateStructuredSectionsAndIndexContent() {
        MarkdownBrotherDocumentSplitter splitter = new MarkdownBrotherDocumentSplitter(
                new MarkdownHeadingScanner(new DocumentSectionIdGenerator()), new TextWindowSplitter(), new DocumentChunkIdGenerator());
        DocumentSplitContext context = new DocumentSplitContext(1L, "测试文档", "demo.md", FileType.MARKDOWN,
                "original/demo.md", null, "parsed/demo.md", null, "text/markdown", "# 标题\n## 子标题\n正文", null,
                new SplitConfigRequest(SplitStrategy.BROTHER_MARKDOWN, 100, 0,
                        new MarkdownSplitOptions(3, true, true, true), null, null));

        DocumentSplitResult splitResult = splitter.split(context);

        assertThat(splitResult.structured()).isTrue();
        assertThat(splitResult.sections()).hasSize(2);
        assertThat(splitResult.chunks()).singleElement().satisfies(draft -> {
            assertThat(draft.sectionId()).isEqualTo(splitResult.sections().get(1).sectionId());
            assertThat(draft.indexContent()).isEqualTo("测试文档\n标题 > 子标题\n正文");
        });
    }

    @Test
    void splitShouldFallbackToUnstructuredChunksWhenContentHasNoHeading() {
        MarkdownBrotherDocumentSplitter splitter = new MarkdownBrotherDocumentSplitter(
                new MarkdownHeadingScanner(new DocumentSectionIdGenerator()), new TextWindowSplitter(), new DocumentChunkIdGenerator());
        DocumentSplitContext context = new DocumentSplitContext(1L, "测试文档", "demo.md", FileType.MARKDOWN,
                "original/demo.md", null, "parsed/demo.md", null, "text/markdown", "没有标题的正文", null,
                new SplitConfigRequest(SplitStrategy.BROTHER_MARKDOWN, 100, 0,
                        new MarkdownSplitOptions(3, false, true, true), null, null));

        DocumentSplitResult splitResult = splitter.split(context);

        assertThat(splitResult.structured()).isFalse();
        assertThat(splitResult.chunks()).singleElement().satisfies(draft -> {
            assertThat(draft.sectionId()).isNull();
            assertThat(draft.indexContent()).isEqualTo(draft.text());
        });
    }
}
