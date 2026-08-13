package com.nexarag.document.splitter.text;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.model.dto.RegexSplitOptions;
import com.nexarag.document.model.dto.SplitConfigRequest;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.model.bo.split.ChunkDraft;
import com.nexarag.document.toolkit.DocumentChunkIdGenerator;
import com.nexarag.document.model.bo.split.DocumentSplitContext;
import com.nexarag.document.model.bo.split.DocumentSplitResult;
import com.nexarag.document.splitter.support.TextWindowSplitter;
import com.nexarag.document.toolkit.RegexSafetyValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 正则文本切分器测试。
 */
class RegexTextDocumentSplitterTest {

    @Test
    void splitShouldSplitBySeparatorAndMergeSmallParts() {
        RegexTextDocumentSplitter splitter = new RegexTextDocumentSplitter(new TextWindowSplitter(),
                new DocumentChunkIdGenerator(), new RegexSafetyValidator());
        DocumentSplitContext context = new DocumentSplitContext(1L, "测试", "demo.txt", FileType.TEXT,
                "original/demo.txt", null, "parsed/demo.txt", null, "text/plain", "第一段\n\n第二段\n\n第三段", null,
                new SplitConfigRequest(SplitStrategy.REGEX_TEXT, 8, 2, null,
                        new RegexSplitOptions("\n\n", null, false), null));

        DocumentSplitResult splitResult = splitter.split(context);
        List<ChunkDraft> drafts = splitResult.chunks();

        assertThat(drafts).hasSize(2);
        assertThat(splitResult.sections()).isEmpty();
        assertThat(splitResult.structured()).isFalse();
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.skipIndex()).isFalse());
        assertThat(drafts).allSatisfy(draft -> {
            assertThat(draft.sectionId()).isNull();
            assertThat(draft.indexContent()).isEqualTo(draft.text());
        });
        assertThat(drafts.getFirst().text()).contains("第一段", "第二段");
    }

    @Test
    void splitShouldRejectNestedQuantifierRegex() {
        RegexTextDocumentSplitter splitter = new RegexTextDocumentSplitter(
                new TextWindowSplitter(), new DocumentChunkIdGenerator(), new RegexSafetyValidator());
        SplitConfigRequest splitConfig = new SplitConfigRequest(
                SplitStrategy.REGEX_TEXT, 1000, 100, null,
                new RegexSplitOptions(null, "(a+)+", false), null);
        DocumentSplitContext context = new DocumentSplitContext(
                1L, "测试文档", "demo.txt", FileType.TEXT,
                "original/demo.txt", null, null, null, "text/plain",
                "aaaaaaaaaaaaaaaa", null, splitConfig);

        assertThatThrownBy(() -> splitter.split(context))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("嵌套量词");
    }
}
