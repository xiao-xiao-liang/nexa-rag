package com.nexarag.document.splitter.structure;

import com.nexarag.document.enums.HeadingEvidenceSource;
import com.nexarag.document.toolkit.extractor.PdfLayoutHeadingEvidenceExtractor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** PDF 相对字号标题证据提取器测试。 */
class PdfLayoutHeadingEvidenceExtractorTest {

    @Test
    void extractShouldInferLevelOnlyFromRelativeFontSizeInSameDocument() throws Exception {
        String middleJson = """
                {"blocks":[
                  {"type":"title","text":"第一章","font_size":20,"page_idx":0},
                  {"type":"title","text":"背景","font_size":16,"page_idx":0},
                  {"type":"title","text":"第二章","font_size":20,"page_idx":1}
                ]}
                """;

        assertThat(new PdfLayoutHeadingEvidenceExtractor().extract(
                new ByteArrayInputStream(middleJson.getBytes(StandardCharsets.UTF_8))))
                .extracting(evidence -> evidence.title(), evidence -> evidence.declaredLevel(),
                        evidence -> evidence.source(), evidence -> evidence.pageNumber())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("第一章", 1, HeadingEvidenceSource.PDF_LAYOUT, 1),
                        org.assertj.core.groups.Tuple.tuple("背景", 2, HeadingEvidenceSource.PDF_LAYOUT, 1),
                        org.assertj.core.groups.Tuple.tuple("第二章", 1, HeadingEvidenceSource.PDF_LAYOUT, 2));
    }
}
