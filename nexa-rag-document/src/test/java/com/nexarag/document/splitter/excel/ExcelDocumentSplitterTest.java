package com.nexarag.document.splitter.excel;

import com.nexarag.document.enums.ExcelSplitMode;
import com.nexarag.document.model.dto.ExcelSplitOptions;
import com.nexarag.document.model.dto.SplitConfigRequest;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.model.bo.split.ChunkDraft;
import com.nexarag.document.toolkit.DocumentChunkIdGenerator;
import com.nexarag.document.model.bo.split.DocumentSplitContext;
import com.nexarag.document.model.bo.split.DocumentSplitResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Excel/CSV 切分器测试。
 */
class ExcelDocumentSplitterTest {

    @Test
    void splitShouldReadCsvAndRenderKeyValueChunks() {
        ExcelDocumentSplitter splitter = new ExcelDocumentSplitter(new DocumentChunkIdGenerator());
        byte[] bytes = "姓名,部门\n张三,研发\n李四,测试\n".getBytes(StandardCharsets.UTF_8);
        DocumentSplitContext context = new DocumentSplitContext(1L, "测试", "demo.csv", FileType.EXCEL,
                "original/demo.csv", null, null, null, null, null, bytes,
                new SplitConfigRequest(SplitStrategy.EXCEL, 100, 0, null, null,
                        new ExcelSplitOptions(ExcelSplitMode.KEY_VALUE, true, null, null)));

        DocumentSplitResult splitResult = splitter.split(context);
        List<ChunkDraft> drafts = splitResult.chunks();

        assertThat(drafts).hasSize(1);
        assertThat(splitResult.sections()).isEmpty();
        assertThat(splitResult.structured()).isFalse();
        assertThat(drafts).allSatisfy(draft -> {
            assertThat(draft.sectionId()).isNull();
            assertThat(draft.indexContent()).isEqualTo(draft.text());
        });
        assertThat(drafts.getFirst().text()).contains("姓名：张三", "部门：研发", "姓名：李四");
        assertThat(drafts.getFirst().metadata()).containsEntry("sheetName", "Sheet1");
    }
}
