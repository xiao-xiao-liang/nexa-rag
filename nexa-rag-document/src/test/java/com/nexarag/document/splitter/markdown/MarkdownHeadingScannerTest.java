package com.nexarag.document.splitter.markdown;

import com.nexarag.document.model.bo.split.MarkdownSection;
import com.nexarag.document.model.dto.MarkdownSplitOptions;
import com.nexarag.document.enums.HeadingEvidenceSource;
import com.nexarag.document.model.bo.structure.DocumentStructureBO;
import com.nexarag.document.model.bo.structure.ResolvedHeadingBO;
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

    @Test
    void scanWithResolvedStructureShouldKeepOverLimitHeadingInParentBody() {
        MarkdownHeadingScanner scanner = new MarkdownHeadingScanner(new DocumentSectionIdGenerator());
        String content = "## 1.7 和 1.8 区别\n说明\n## 一、核心数据结构差异\n加粗正文\n## 二、节点插入方式的变化\n更多正文";
        DocumentStructureBO structure = new DocumentStructureBO(List.of(
                new ResolvedHeadingBO("1.7 和 1.8 区别", 3, 1, 1, HeadingEvidenceSource.PDF_LAYOUT, 0.82D),
                new ResolvedHeadingBO("一、核心数据结构差异", 4, 3, 1, HeadingEvidenceSource.PDF_LAYOUT, 0.82D),
                new ResolvedHeadingBO("二、节点插入方式的变化", 4, 5, 1, HeadingEvidenceSource.PDF_LAYOUT, 0.82D)
        ), List.of());

        List<MarkdownSection> sections = scanner.scan(content, new MarkdownSplitOptions(3, false, true, true),
                1L, structure);

        assertThat(sections).singleElement().satisfies(section -> {
            assertThat(section.title()).isEqualTo("1.7 和 1.8 区别");
            assertThat(section.bodyText()).contains("#### 一、核心数据结构差异", "加粗正文",
                    "#### 二、节点插入方式的变化", "更多正文")
                    .doesNotContain("\n## 一、核心数据结构差异", "\n## 二、节点插入方式的变化");
        });
    }
}
