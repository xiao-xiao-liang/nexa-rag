package com.nexarag.document.splitter.structure;

import com.nexarag.document.enums.HeadingEvidenceSource;
import com.nexarag.document.model.bo.structure.HeadingEvidenceBO;
import com.nexarag.document.toolkit.resolver.HeadingLineLocator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Pandoc Markdown 标题行定位测试。 */
class HeadingLineLocatorTest {

    @Test
    void locateShouldMatchPandocEscapedAndBoldNumberedHeading() {
        HeadingLineLocator locator = new HeadingLineLocator();

        var structure = locator.locate("1\\. **ZSET原理**\n正文", List.of(
                new HeadingEvidenceBO("1. ZSET原理", 2, 1, HeadingEvidenceSource.WORD_OUTLINE, 1.0D, null)));

        assertThat(structure.headings()).singleElement().satisfies(heading -> {
            assertThat(heading.lineNumber()).isEqualTo(1);
            assertThat(heading.title()).isEqualTo("1. ZSET原理");
        });
        assertThat(structure.diagnostics()).isEmpty();
    }
}
