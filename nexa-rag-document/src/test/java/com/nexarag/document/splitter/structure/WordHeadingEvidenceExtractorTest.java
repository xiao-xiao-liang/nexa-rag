package com.nexarag.document.splitter.structure;

import com.nexarag.document.enums.HeadingEvidenceSource;
import com.nexarag.document.toolkit.extractor.WordHeadingEvidenceExtractor;
import com.nexarag.document.toolkit.resolver.HeadingNumberingParser;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Word 大纲层级标题证据提取测试。 */
class WordHeadingEvidenceExtractorTest {

    @Test
    void extractShouldUseOutlineLevelWhenStyleIdHasNoSemanticName() throws Exception {
        WordHeadingEvidenceExtractor extractor = new WordHeadingEvidenceExtractor(new HeadingNumberingParser());

        assertThat(extractor.extract(new ByteArrayInputStream(createOutlineDocument())))
                .singleElement()
                .satisfies(evidence -> {
                    assertThat(evidence.title()).isEqualTo("3.1 Big Key 是如何产生的");
                    assertThat(evidence.declaredLevel()).isEqualTo(3);
                    assertThat(evidence.source()).isEqualTo(HeadingEvidenceSource.WORD_OUTLINE);
                    assertThat(evidence.confidence()).isEqualTo(1.0D);
                });
    }

    private byte[] createOutlineDocument() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setStyle("3");
            paragraph.getCTP().getPPr().addNewOutlineLvl().setVal(BigInteger.valueOf(2));
            paragraph.createRun().setText("3.1 Big Key 是如何产生的");
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
