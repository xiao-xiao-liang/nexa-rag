package com.nexarag.document.splitter.markdown;

import com.nexarag.document.dto.MarkdownSplitOptions;
import com.nexarag.document.dto.SplitConfigRequest;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.splitter.ChunkDraft;
import com.nexarag.document.splitter.DocumentChunkIdGenerator;
import com.nexarag.document.splitter.DocumentSplitContext;
import com.nexarag.document.splitter.DocumentSplitResult;
import com.nexarag.document.splitter.DocumentSectionIdGenerator;
import com.nexarag.document.splitter.support.TextWindowSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Markdown 父子切分器测试。
 */
class MarkdownParentDocumentSplitterTest {

    @Test
    void splitShouldCreateStructuredSectionsAndOnlyIndexSectionsWithBody() {
        MarkdownParentDocumentSplitter splitter = new MarkdownParentDocumentSplitter(
                new MarkdownHeadingScanner(new DocumentSectionIdGenerator()), new TextWindowSplitter(), new DocumentChunkIdGenerator());
        String content = "# 3 环境准备\n## 3.1 建议的项目目录\n/home/liang/swift/";
        DocumentSplitContext context = new DocumentSplitContext(1L, "Swift 开发文档", "demo.md", FileType.MARKDOWN,
                "original/demo.md", null, "parsed/demo.md", null, "text/markdown", content, null,
                new SplitConfigRequest(SplitStrategy.PARENT_MARKDOWN, 100, 5,
                        new MarkdownSplitOptions(3, true, true, true), null, null));

        DocumentSplitResult splitResult = splitter.split(context);
        List<ChunkDraft> drafts = splitResult.chunks();

        assertThat(splitResult.structured()).isTrue();
        assertThat(splitResult.sections()).hasSize(2);
        assertThat(splitResult.sections().getFirst().parentSectionId()).isNull();
        assertThat(splitResult.sections().get(1).parentSectionId())
                .isEqualTo(splitResult.sections().getFirst().sectionId());
        assertThat(drafts).singleElement().satisfies(draft -> {
            assertThat(draft.sectionId()).isEqualTo(splitResult.sections().get(1).sectionId());
            assertThat(draft.text()).contains("/home/liang/swift/");
            assertThat(draft.indexContent()).isEqualTo("Swift 开发文档\n3 环境准备 > 3.1 建议的项目目录\n/home/liang/swift/");
        });
    }

    @Test
    void splitShouldKeepSectionRelationWhenOversizedBodyCreatesParentAndChildren() {
        MarkdownParentDocumentSplitter splitter = new MarkdownParentDocumentSplitter(
                new MarkdownHeadingScanner(new DocumentSectionIdGenerator()), new TextWindowSplitter(), new DocumentChunkIdGenerator());
        DocumentSplitContext context = new DocumentSplitContext(1L, "测试", "demo.md", FileType.MARKDOWN,
                "original/demo.md", null, "parsed/demo.md", null, "text/markdown", "# 标题\n" + "正文".repeat(80), null,
                new SplitConfigRequest(SplitStrategy.PARENT_MARKDOWN, 50, 5,
                        new MarkdownSplitOptions(3, false, true, true), null, null));

        DocumentSplitResult splitResult = splitter.split(context);
        List<ChunkDraft> drafts = splitResult.chunks();

        assertThat(splitResult.structured()).isTrue();
        assertThat(drafts).hasSizeGreaterThan(2);
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.sectionId())
                .isEqualTo(splitResult.sections().getFirst().sectionId()));
        ChunkDraft parent = drafts.stream().filter(ChunkDraft::skipIndex).findFirst().orElseThrow();
        assertThat(drafts.stream().filter(draft -> !draft.skipIndex()).toList())
                .allSatisfy(draft -> assertThat(draft.parentChunkId()).isEqualTo(parent.chunkId()));
    }

    @Test
    void splitShouldKeepHeadingInRawTextWhenStripHeadersIsFalse() {
        MarkdownParentDocumentSplitter splitter = new MarkdownParentDocumentSplitter(
                new MarkdownHeadingScanner(new DocumentSectionIdGenerator()), new TextWindowSplitter(), new DocumentChunkIdGenerator());
        DocumentSplitContext context = new DocumentSplitContext(1L, "测试", "demo.md", FileType.MARKDOWN,
                "original/demo.md", null, "parsed/demo.md", null, "text/markdown", "# 标题\n正文", null,
                new SplitConfigRequest(SplitStrategy.PARENT_MARKDOWN, 100, 0,
                        new MarkdownSplitOptions(3, false, true, true), null, null));

        DocumentSplitResult splitResult = splitter.split(context);

        assertThat(splitResult.chunks()).singleElement().satisfies(draft -> {
            assertThat(draft.text()).isEqualTo("# 标题\n正文");
            assertThat(draft.indexContent()).isEqualTo("测试\n标题\n正文");
        });
    }

    @Test
    void splitShouldExcludeHeadingFromOversizedSectionIndexWindowsWhenStripHeadersIsFalse() {
        MarkdownParentDocumentSplitter splitter = new MarkdownParentDocumentSplitter(
                new MarkdownHeadingScanner(new DocumentSectionIdGenerator()), new TextWindowSplitter(), new DocumentChunkIdGenerator());
        DocumentSplitContext context = new DocumentSplitContext(1L, "测试", "demo.md", FileType.MARKDOWN,
                "original/demo.md", null, "parsed/demo.md", null, "text/markdown", "# 标题\n" + "正文".repeat(80), null,
                new SplitConfigRequest(SplitStrategy.PARENT_MARKDOWN, 50, 5,
                        new MarkdownSplitOptions(3, false, true, true), null, null));

        DocumentSplitResult splitResult = splitter.split(context);
        ChunkDraft parent = splitResult.chunks().stream().filter(ChunkDraft::skipIndex).findFirst().orElseThrow();
        ChunkDraft firstWindow = splitResult.chunks().stream().filter(draft -> !draft.skipIndex()).findFirst().orElseThrow();

        assertThat(parent.indexContent()).startsWith("测试\n标题\n正文").doesNotContain("# 标题");
        assertThat(firstWindow.indexContent()).startsWith("测试\n标题\n正文").doesNotContain("# 标题");
    }

    @Test
    void splitShouldKeepHeadingFragmentsOutOfIndexWindowsWhenOverlapIsHigh() {
        MarkdownParentDocumentSplitter splitter = new MarkdownParentDocumentSplitter(
                new MarkdownHeadingScanner(new DocumentSectionIdGenerator()), new TextWindowSplitter(), new DocumentChunkIdGenerator());
        DocumentSplitContext context = new DocumentSplitContext(1L, "测试", "demo.md", FileType.MARKDOWN,
                "original/demo.md", null, "parsed/demo.md", null, "text/markdown", "# 章节标题标题标题标题标题\n" + "正文".repeat(20), null,
                new SplitConfigRequest(SplitStrategy.PARENT_MARKDOWN, 10, 5,
                        new MarkdownSplitOptions(3, false, true, true), null, null));

        DocumentSplitResult splitResult = splitter.split(context);
        String indexPrefix = "测试\n章节标题标题标题标题标题\n";

        List<ChunkDraft> rawWindows = splitResult.chunks().stream().filter(draft -> !draft.skipIndex()).toList();
        assertThat(rawWindows.getFirst().text()).contains("标题", "正文");
        assertThat(splitResult.chunks()).allSatisfy(draft -> {
            String indexBody = draft.indexContent().substring(indexPrefix.length());
            assertThat(indexBody).doesNotContain("章", "节", "标", "题");
        });
        assertThat(splitResult.chunks()).anySatisfy(draft -> assertThat(draft.indexContent()).contains("正文"));
    }

    @Test
    void splitShouldNotRepeatShortBodyWhenLongHeadingCreatesMoreRawWindows() {
        MarkdownParentDocumentSplitter splitter = new MarkdownParentDocumentSplitter(
                new MarkdownHeadingScanner(new DocumentSectionIdGenerator()), new TextWindowSplitter(), new DocumentChunkIdGenerator());
        String title = "超长章节标题".repeat(5);
        String body = "正文唯一内容";
        DocumentSplitContext context = new DocumentSplitContext(1L, "测试", "demo.md", FileType.MARKDOWN,
                "original/demo.md", null, "parsed/demo.md", null, "text/markdown", "# " + title + "\n" + body, null,
                new SplitConfigRequest(SplitStrategy.PARENT_MARKDOWN, 10, 0,
                        new MarkdownSplitOptions(3, false, true, true), null, null));

        DocumentSplitResult splitResult = splitter.split(context);
        String indexPrefix = "测试\n" + title + "\n";
        List<ChunkDraft> indexableChunks = splitResult.chunks().stream().filter(draft -> !draft.skipIndex()).toList();

        assertThat(indexableChunks).singleElement().satisfies(draft -> {
            assertThat(draft.text()).contains(body).contains("标题");
            assertThat(draft.indexContent()).isEqualTo(indexPrefix + body);
        });
    }

    @Test
    void splitShouldFallbackToUnstructuredChunksWhenHeadingTreeIsInvalid() {
        MarkdownParentDocumentSplitter splitter = new MarkdownParentDocumentSplitter(
                new MarkdownHeadingScanner(new DocumentSectionIdGenerator()), new TextWindowSplitter(), new DocumentChunkIdGenerator());
        DocumentSplitContext context = new DocumentSplitContext(1L, "测试", "demo.md", FileType.MARKDOWN,
                "original/demo.md", null, "parsed/demo.md", null, "text/markdown", "# 一级\n### 三级\n正文", null,
                new SplitConfigRequest(SplitStrategy.PARENT_MARKDOWN, 100, 0,
                        new MarkdownSplitOptions(3, false, true, true), null, null));

        DocumentSplitResult splitResult = splitter.split(context);

        assertThat(splitResult.structured()).isFalse();
        assertThat(splitResult.sections()).isEmpty();
        assertThat(splitResult.chunks()).allSatisfy(draft -> {
            assertThat(draft.sectionId()).isNull();
            assertThat(draft.indexContent()).isEqualTo(draft.text());
        });
    }

    @Test
    void scannerShouldIgnoreHeadingInsideCodeBlock() {
        MarkdownHeadingScanner scanner = new MarkdownHeadingScanner(new DocumentSectionIdGenerator());
        String content = "# A\n```\n# not heading\n```\n## B\n正文";

        List<MarkdownSection> sections = scanner.scan(content, new MarkdownSplitOptions(3, false, true, true));

        assertThat(sections).hasSize(2);
        assertThat(sections.getFirst().text()).contains("# not heading");
        assertThat(sections.get(1).title()).isEqualTo("B");
    }
}
