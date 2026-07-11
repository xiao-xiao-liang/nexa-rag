package com.nexarag.document.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexarag.document.entity.Document;
import com.nexarag.document.entity.DocumentChunk;
import com.nexarag.document.enums.ChunkStatus;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.vo.DocumentChunkVO;
import com.nexarag.document.vo.DocumentSummaryVO;
import com.nexarag.document.vo.PageVO;
import org.junit.jupiter.api.Test;

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
                .build();

        DocumentSummaryVO vo = DocumentConverter.toSummaryVO(document);

        assertThat(vo.documentId()).isEqualTo(1L);
        assertThat(vo.title()).isEqualTo("测试文档");
        assertThat(vo.fileType()).isEqualTo(FileType.PDF);
        assertThat(vo.status()).isEqualTo(DocumentStatus.UPLOADED);
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

        assertThat(result.current()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.total()).isEqualTo(41);
        assertThat(result.records()).extracting(DocumentChunkVO::chunkId).containsExactly("chunk-1");
    }
}
