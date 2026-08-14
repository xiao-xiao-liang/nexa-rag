package com.nexarag.document.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexarag.common.web.PageVO;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.enums.ChunkStatus;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.document.model.vo.DocumentChunkStatisticsVO;
import com.nexarag.document.model.vo.DocumentChunkVO;
import com.nexarag.document.model.vo.DocumentOverviewVO;
import com.nexarag.document.model.vo.DocumentSummaryVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档转换器测试。
 */
class DocumentConverterTest {

    @Test
    void shouldConvertEntityToSummaryVO() {
        Document document = Document.builder()
                .documentId(1L)
                .title("测试文档")
                .originalFileName("demo.pdf")
                .fileType(FileType.PDF)
                .status(DocumentStatus.UPLOADED)
                .createBy("张三")
                .updateTime(LocalDateTime.of(2026, 8, 13, 10, 30))
                .build();

        DocumentSummaryVO vo = DocumentConverter.toSummaryVO(document);

        assertThat(vo.documentId()).isEqualTo(1L);
        assertThat(vo.title()).isEqualTo("测试文档");
        assertThat(vo.fileType()).isEqualTo(FileType.PDF);
        assertThat(vo.status()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(vo.createBy()).isEqualTo("张三");
        assertThat(vo.updatedTime()).isEqualTo(LocalDateTime.of(2026, 8, 13, 10, 30));
    }

    @Test
    void toChunkPageVOShouldKeepPaginationMetadata() {
        Page<DocumentChunk> page = Page.of(2, 20);
        page.setTotal(41);
        page.setRecords(List.of(DocumentChunk.builder()
                .chunkId("chunk-1")
                .documentId(1L)
                .chunkOrder(20)
                .text("测试片段")
                .status(ChunkStatus.PENDING_INDEX)
                .build()));

        PageVO<DocumentChunkVO> result = DocumentConverter.toChunkPageVO(page);

        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotal()).isEqualTo(41);
        assertThat(result.getRecords()).extracting(DocumentChunkVO::chunkId).containsExactly("chunk-1");
    }

    @Test
    void toOverviewVOShouldCarryDocumentFieldsAndStatistics() {
        Document document = Document.builder()
                .documentId(2L)
                .title("概览文档")
                .description("描述")
                .originalFileName("guide.md")
                .fileType(FileType.MARKDOWN)
                .fileSize(2048L)
                .status(DocumentStatus.INDEXED)
                .sourceType(ExternalDocumentSourceType.LOCAL)
                .processConfigJson("{\"splitConfig\":{}}")
                .createTime(LocalDateTime.of(2026, 8, 13, 9, 0))
                .updateTime(LocalDateTime.of(2026, 8, 13, 9, 30))
                .build();
        DocumentChunkStatisticsVO statistics = new DocumentChunkStatisticsVO(12, 10, 1, 0, 1);

        DocumentOverviewVO vo = DocumentConverter.toOverviewVO(document, statistics);

        assertThat(vo.documentId()).isEqualTo(2L);
        assertThat(vo.fileType()).isEqualTo(FileType.MARKDOWN);
        assertThat(vo.sourceType()).isEqualTo(ExternalDocumentSourceType.LOCAL);
        assertThat(vo.processConfigJson()).contains("splitConfig");
        assertThat(vo.createTime()).isEqualTo(LocalDateTime.of(2026, 8, 13, 9, 0));
        assertThat(vo.updateTime()).isEqualTo(LocalDateTime.of(2026, 8, 13, 9, 30));
        assertThat(vo.chunkStatistics().indexed()).isEqualTo(10);
        assertThat(vo.chunkStatistics().failed()).isEqualTo(1);
        assertThat(vo.chunkStatistics().pending()).isEqualTo(1);
    }
}
