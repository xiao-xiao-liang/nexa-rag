package com.nexarag.document.splitter.markdown;

import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.model.dto.MarkdownSplitOptions;
import com.nexarag.document.model.dto.SplitConfigRequest;
import com.nexarag.document.model.bo.split.ChunkDraft;
import com.nexarag.document.model.bo.split.DocumentSplitContext;
import com.nexarag.document.model.bo.split.DocumentSplitResult;
import com.nexarag.document.splitter.support.TextWindowSplitter;
import com.nexarag.document.toolkit.DocumentChunkIdGenerator;
import com.nexarag.document.toolkit.DocumentSectionIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Markdown 章节构建器安全窗口接入测试。 */
class MarkdownSectionStructureBuilderSafeWindowTest {

    @Test
    void buildShouldKeepOversizedHtmlTableWellFormedInEachChildChunk() {
        String table = "<table><tr><td>名称</td><td>说明</td></tr>"
                + "<tr><td>ArrayList</td><td>动态数组，随机访问快</td></tr>"
                + "<tr><td>LinkedList</td><td>双向链表，插入删除快</td></tr>"
                + "</table>";
        DocumentSplitContext context = new DocumentSplitContext(1L, "测试", "demo.md", FileType.MARKDOWN,
                "original/demo.md", null, "parsed/demo.md", null, "text/markdown", "# List\n" + table, null,
                new SplitConfigRequest(SplitStrategy.PARENT_MARKDOWN, 90, 10,
                        new MarkdownSplitOptions(3, false, true, true), null, null));
        TextWindowSplitter textWindowSplitter = new TextWindowSplitter();
        MarkdownSectionStructureBuilder builder = new MarkdownSectionStructureBuilder(
                new MarkdownHeadingScanner(new DocumentSectionIdGenerator()),
                new MarkdownSafeWindowSplitter(textWindowSplitter), new DocumentChunkIdGenerator(), null);

        DocumentSplitResult splitResult = builder.build(context, SplitStrategy.PARENT_MARKDOWN);
        List<ChunkDraft> children = splitResult.chunks().stream().filter(draft -> !draft.skipIndex()).toList();

        assertThat(children).hasSizeGreaterThan(1);
        assertThat(children).allSatisfy(child -> {
            assertThat(child.text()).contains("<table>", "</table>");
            assertThat(countOccurrences(child.text(), "<tr")).isEqualTo(countOccurrences(child.text(), "</tr>"));
        });
    }

    private int countOccurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
