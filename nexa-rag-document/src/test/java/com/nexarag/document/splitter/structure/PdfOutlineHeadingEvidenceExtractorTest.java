package com.nexarag.document.splitter.structure;

import com.nexarag.document.toolkit.extractor.PdfOutlineHeadingEvidenceExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.parser.workspace.BoundedFileTransfer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** PDF 书签标题证据提取器测试。 */
class PdfOutlineHeadingEvidenceExtractorTest {

    @Test
    void extractShouldKeepOutlineLevelAndDestinationPage() throws Exception {
        ArtifactProcessingProperties properties = new ArtifactProcessingProperties();
        properties.setMaxWorkspaceBytes(1024 * 1024L);
        PdfOutlineHeadingEvidenceExtractor extractor = new PdfOutlineHeadingEvidenceExtractor(
                new BoundedFileTransfer(), properties);

        assertThat(extractor.extract(new ByteArrayInputStream(createPdfWithOutline())))
                .extracting(evidence -> evidence.title(), evidence -> evidence.declaredLevel(),
                        evidence -> evidence.pageNumber())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("第一章", 1, 1),
                        org.assertj.core.groups.Tuple.tuple("1.1 背景", 2, 2));
    }

    private byte[] createPdfWithOutline() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            PDDocumentOutline outline = new PDDocumentOutline();
            document.getDocumentCatalog().setDocumentOutline(outline);
            PDOutlineItem chapter = item("第一章", document.getPages().get(0));
            PDOutlineItem section = item("1.1 背景", document.getPages().get(1));
            chapter.addLast(section);
            outline.addLast(chapter);
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private PDOutlineItem item(String title, PDPage page) {
        PDPageFitDestination destination = new PDPageFitDestination();
        destination.setPage(page);
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        item.setDestination(destination);
        return item;
    }
}
