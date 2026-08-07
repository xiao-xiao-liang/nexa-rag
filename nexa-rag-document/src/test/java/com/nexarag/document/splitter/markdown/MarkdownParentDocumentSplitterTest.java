package com.nexarag.document.splitter.markdown;

import com.nexarag.document.dto.MarkdownSplitOptions;
import com.nexarag.document.dto.SplitConfigRequest;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.splitter.ChunkDraft;
import com.nexarag.document.splitter.DocumentChunkIdGenerator;
import com.nexarag.document.splitter.DocumentSplitContext;
import com.nexarag.document.splitter.support.TextWindowSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Markdown 父子切分器测试。
 */
class MarkdownParentDocumentSplitterTest {

    @Test
    void splitShouldCreateParentAndChildrenForOversizedSection() {
        MarkdownParentDocumentSplitter splitter = new MarkdownParentDocumentSplitter(
                new MarkdownHeadingScanner(), new TextWindowSplitter(), new DocumentChunkIdGenerator());
        String content = "# 标题\n" + "内容".repeat(80);
        DocumentSplitContext context = new DocumentSplitContext(1L, "测试", "demo.md", FileType.MARKDOWN,
                "original/demo.md", null, "parsed/demo.md", null, "text/markdown", content, null,
                new SplitConfigRequest(SplitStrategy.PARENT_MARKDOWN, 50, 5,
                        new MarkdownSplitOptions(3, false, true, true), null, null));

        List<ChunkDraft> drafts = splitter.split(context);

        assertThat(drafts).hasSizeGreaterThan(1);
        assertThat(drafts.getFirst().skipIndex()).isTrue();
        assertThat(drafts.getFirst().parentChunkId()).isNull();
        assertThat(drafts.subList(1, drafts.size()))
                .allSatisfy(draft -> assertThat(draft.parentChunkId()).isEqualTo(drafts.getFirst().chunkId()));
    }

    @Test
    void scannerShouldIgnoreHeadingInsideCodeBlock() {
        MarkdownHeadingScanner scanner = new MarkdownHeadingScanner();
        String content = "# A\n```\n# not heading\n```\n## B\n正文";

        List<MarkdownSection> sections = scanner.scan(content, new MarkdownSplitOptions(3, false, true, true));

        assertThat(sections).hasSize(2);
        assertThat(sections.getFirst().text()).contains("# not heading");
        assertThat(sections.get(1).title()).isEqualTo("B");
    }
}
