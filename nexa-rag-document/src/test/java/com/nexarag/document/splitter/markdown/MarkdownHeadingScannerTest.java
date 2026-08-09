package com.nexarag.document.splitter.markdown;

import com.nexarag.document.model.dto.MarkdownSplitOptions;
import com.nexarag.document.toolkit.DocumentSectionIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Markdown 标题扫描器测试。
 */
class MarkdownHeadingScannerTest {

    @Test
    void scanShouldBuildHeadingTreeAndKeepDirectBodyRange() {
        MarkdownHeadingScanner scanner = new MarkdownHeadingScanner(new DocumentSectionIdGenerator());

        List<MarkdownSection> sections = scanner.scan("# 环境\n## 目录\n/home/liang/swift/",
                new MarkdownSplitOptions(3, false, true, true));

        assertThat(sections).hasSize(2);
        MarkdownSection parent = sections.getFirst();
        MarkdownSection child = sections.get(1);
        assertThat(parent.sectionId()).isNotNull();
        assertThat(parent.parentSectionId()).isNull();
        assertThat(parent.headingPath()).containsExactly("环境");
        assertThat(parent.bodyText()).isBlank();
        assertThat(child.parentSectionId()).isEqualTo(parent.sectionId());
        assertThat(child.headingPath()).containsExactly("环境", "目录");
        assertThat(child.startLine()).isEqualTo(2);
        assertThat(child.endLine()).isEqualTo(3);
        assertThat(child.bodyStartLine()).isEqualTo(3);
        assertThat(child.bodyEndLine()).isEqualTo(3);
        assertThat(child.bodyText()).isEqualTo("/home/liang/swift/");
    }
}
