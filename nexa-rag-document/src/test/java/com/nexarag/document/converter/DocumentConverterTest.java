package com.nexarag.document.converter;

import com.nexarag.document.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.vo.DocumentSummaryVO;
import org.junit.jupiter.api.Test;

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
}
