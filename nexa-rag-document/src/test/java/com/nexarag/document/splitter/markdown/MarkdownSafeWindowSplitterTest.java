package com.nexarag.document.splitter.markdown;

import com.nexarag.document.splitter.support.TextWindowSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Markdown 安全窗口切分器测试。 */
class MarkdownSafeWindowSplitterTest {

    @Test
    void splitShouldKeepLargeHtmlTableClosedAndSplitByRows() {
        MarkdownSafeWindowSplitter splitter = new MarkdownSafeWindowSplitter(new TextWindowSplitter());
        String table = "<table><tr><td>名称</td><td>说明</td></tr>"
                + "<tr><td>ArrayList</td><td>动态数组，随机访问快</td></tr>"
                + "<tr><td>LinkedList</td><td>双向链表，插入删除快</td></tr>"
                + "</table>";

        List<String> windows = splitter.split(table, 90, 10);

        assertThat(windows).hasSizeGreaterThan(1);
        assertThat(windows).allSatisfy(window -> {
            assertThat(window).contains("<table>", "</table>");
            assertThat(countOccurrences(window, "<tr")).isEqualTo(countOccurrences(window, "</tr>"));
        });
        assertThat(windows).anySatisfy(window -> assertThat(window).contains("ArrayList"));
        assertThat(windows).anySatisfy(window -> assertThat(window).contains("LinkedList"));
    }

    @Test
    void splitShouldKeepFencedCodeBlockAndHeadingIntactWhenOversized() {
        MarkdownSafeWindowSplitter splitter = new MarkdownSafeWindowSplitter(new TextWindowSplitter());
        String code = "```java\n" + "System.out.println(\"line\");\n".repeat(12) + "```";

        List<String> windows = splitter.split(code, 60, 10);

        assertThat(windows).singleElement().satisfies(window -> {
            assertThat(window).startsWith("```java");
            assertThat(window).endsWith("```");
        });
    }

    @Test
    void splitShouldCarryTrailingPlainTextIntoNextWindowAsOverlap() {
        MarkdownSafeWindowSplitter splitter = new MarkdownSafeWindowSplitter(new TextWindowSplitter());
        String firstParagraph = "前文".repeat(20);
        String secondParagraph = "后文".repeat(20);

        List<String> windows = splitter.split(firstParagraph + "\n\n" + secondParagraph, 50, 6);

        assertThat(windows).hasSize(2);
        assertThat(windows.get(1)).startsWith(firstParagraph.substring(firstParagraph.length() - 6));
        assertThat(windows.get(1)).contains(secondParagraph);
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
