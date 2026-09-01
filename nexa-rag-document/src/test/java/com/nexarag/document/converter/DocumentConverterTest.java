package com.nexarag.document.converter;

import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.model.vo.DocumentChunkStatisticsVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档读模型转换器测试。
 */
class DocumentConverterTest {

    @Test
    void shouldProjectContentAndStatusFromActiveVersion() {
        Document document = Document.builder().documentId(1L).title("测试文档").description("描述")
                .activeVersionId(11L).build();
        DocumentVersionDO activeVersion = DocumentVersionDO.builder().documentId(1L).documentVersionId(11L)
                .originalFileName("v1.md").fileType(FileType.MARKDOWN)
                .status(DocumentVersionStatus.INDEX_READY).processId("process-v1").build();

        var summary = DocumentConverter.toSummaryVO(document, activeVersion);
        var detail = DocumentConverter.toDetailVO(document, activeVersion);
        var overview = DocumentConverter.toOverviewVO(document, activeVersion,
                new DocumentChunkStatisticsVO(1, 1, 0, 0, 0));
        var processStatus = DocumentConverter.toProcessStatusVO(document, activeVersion);

        assertThat(summary.originalFileName()).isEqualTo("v1.md");
        assertThat(summary.status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(detail.originalFileName()).isEqualTo("v1.md");
        assertThat(overview.chunkStatistics().indexed()).isOne();
        assertThat(processStatus.processId()).isEqualTo("process-v1");
    }

    @Test
    void shouldReturnNoActiveVersionEmptyState() {
        Document document = Document.builder().documentId(1L).title("测试文档").build();

        var summary = DocumentConverter.toSummaryVO(document, null);
        var processStatus = DocumentConverter.toProcessStatusVO(document, null);

        assertThat(summary.originalFileName()).isNull();
        assertThat(summary.status()).isNull();
        assertThat(processStatus.processId()).isNull();
        assertThat(processStatus.status()).isNull();
    }
}
