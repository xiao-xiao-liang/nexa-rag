package com.nexarag.document.splitter.structure;

import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.HeadingEvidenceSource;
import com.nexarag.document.model.bo.structure.HeadingEvidenceBO;
import com.nexarag.document.model.bo.structure.StructureArtifactReferenceBO;
import com.nexarag.document.model.bo.split.DocumentSplitContext;
import com.nexarag.document.toolkit.extractor.*;
import com.nexarag.document.toolkit.refiner.HeadingHierarchyRefiner;
import com.nexarag.document.toolkit.refiner.PassthroughHeadingHierarchyRefiner;
import com.nexarag.document.toolkit.resolver.DocumentStructureResolver;
import com.nexarag.document.toolkit.resolver.HeadingHierarchyResolver;
import com.nexarag.document.toolkit.resolver.HeadingLineLocator;
import com.nexarag.document.toolkit.resolver.HeadingNumberingParser;
import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.config.DocumentStructureProperties;
import com.nexarag.infra.parser.workspace.BoundedFileTransfer;
import com.nexarag.infra.storage.service.FileStorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** PDF 结构证据融合测试。 */
class DocumentStructureResolverTest {

    @Test
    void resolveShouldPreferPdfOutlineOverMarkdownHeading() throws Exception {
        FileStorageService storageService = mock(FileStorageService.class);
        when(storageService.load("original/1.pdf")).thenReturn(new ByteArrayInputStream(createOutlinedPdf()));
        DocumentStructureProperties structureProperties = new DocumentStructureProperties();
        ArtifactProcessingProperties artifactProperties = new ArtifactProcessingProperties();
        artifactProperties.setMaxWorkspaceBytes(1024 * 1024L);
        HeadingNumberingParser numberingParser = new HeadingNumberingParser();
        DocumentStructureResolver resolver = new DocumentStructureResolver(
                new MarkdownHeadingEvidenceExtractor(), new HeadingHierarchyResolver(structureProperties),
                new HeadingLineLocator(), new WordHeadingEvidenceExtractor(numberingParser),
                new MinerUHeadingEvidenceExtractor(), new MinerUContentListHeadingEvidenceExtractor(), storageService,
                new PdfOutlineHeadingEvidenceExtractor(new BoundedFileTransfer(), artifactProperties),
                new PdfLayoutHeadingEvidenceExtractor(), numberingParser,
                new PassthroughHeadingHierarchyRefiner());

        assertThat(resolver.resolve(new DocumentSplitContext(1L, "PDF", "demo.pdf", FileType.PDF,
                "original/1.pdf", null, "parsed/1/content.md", null, "text/markdown", "# 第一章\n正文",
                null, null)).headings()).singleElement().satisfies(heading -> {
                    assertThat(heading.source()).isEqualTo(HeadingEvidenceSource.PDF_OUTLINE);
                    assertThat(heading.level()).isEqualTo(1);
                    assertThat(heading.lineNumber()).isEqualTo(1);
                    assertThat(heading.originalPageNumber()).isEqualTo(1);
        });
    }

    @Test
    void resolveShouldUseMineruNumberingWhenPdfHasNoOutline() throws Exception {
        FileStorageService storageService = mock(FileStorageService.class);
        when(storageService.load("original/2.pdf")).thenReturn(new ByteArrayInputStream(createPdfWithoutOutline()));
        when(storageService.load("parsed/2/structure/mineru-middle.json")).thenReturn(new ByteArrayInputStream("""
                {"blocks":[{"type":"title","text":"1.2 事务","page_idx":0,"font_size":18}]}
                """.getBytes(StandardCharsets.UTF_8)));
        DocumentStructureResolver resolver = resolver(storageService);

        assertThat(resolver.resolve(new DocumentSplitContext(2L, "PDF", "demo.pdf", FileType.PDF,
                "original/2.pdf", null, "parsed/2/content.md", null, "text/markdown", "## 1.2 事务\n正文",
                null, null, List.of(new StructureArtifactReferenceBO("MINERU_MIDDLE_JSON",
                        "parsed/2/structure/mineru-middle.json", "application/json", 128L)))).headings())
                .singleElement().satisfies(heading -> {
                    assertThat(heading.source()).isEqualTo(HeadingEvidenceSource.PDF_NUMBERING);
                    assertThat(heading.level()).isEqualTo(2);
                });
    }

    @Test
    void resolveShouldPreferOfficialMineruContentListV2LayoutOverFlatMarkdown() throws Exception {
        FileStorageService storageService = mock(FileStorageService.class);
        when(storageService.load("original/4.pdf")).thenReturn(new ByteArrayInputStream(createPdfWithoutOutline()));
        when(storageService.load("parsed/4/structure/mineru-content-list-v2.json"))
                .thenReturn(new ByteArrayInputStream("""
                        [[
                          {"type":"title","content":{"title_content":[{"type":"text","content":"Java集合"}]},"bbox":[0,0,100,34]},
                          {"type":"title","content":{"title_content":[{"type":"text","content":"List"}]},"bbox":[0,0,100,27]},
                          {"type":"title","content":{"title_content":[{"type":"text","content":"1. ArrayList"}]},"bbox":[0,0,100,24]}
                        ]]
                        """.getBytes(StandardCharsets.UTF_8)));
        DocumentStructureResolver resolver = resolver(storageService);

        assertThat(resolver.resolve(new DocumentSplitContext(4L, "PDF", "demo.pdf", FileType.PDF,
                "original/4.pdf", null, "parsed/4/content.md", null, "text/markdown",
                "## Java集合\n\n## List\n\n## 1. ArrayList\n\n## • 非章节项目", null, null,
                List.of(new StructureArtifactReferenceBO("MINERU_CONTENT_LIST_V2_JSON",
                        "parsed/4/structure/mineru-content-list-v2.json", "application/json", 128L))))
                .headings())
                .extracting(heading -> heading.title(), heading -> heading.level(), heading -> heading.source())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Java集合", 1, HeadingEvidenceSource.PDF_LAYOUT),
                        org.assertj.core.groups.Tuple.tuple("List", 2, HeadingEvidenceSource.PDF_LAYOUT),
                        org.assertj.core.groups.Tuple.tuple("1. ArrayList", 3,
                                HeadingEvidenceSource.PDF_LAYOUT));
    }

    @Test
    void resolveShouldApplyHeadingHierarchyRefinerBeforeLocatingLines() {
        FileStorageService storageService = mock(FileStorageService.class);
        HeadingHierarchyRefiner refiner = (documentId, headings) -> List.of(
                new HeadingEvidenceBO("第二章", 1, 0,
                        HeadingEvidenceSource.LLM, 0.92D, null));
        DocumentStructureResolver resolver = resolver(storageService, refiner);

        assertThat(resolver.resolve(new DocumentSplitContext(3L, "WORD", "demo.docx", FileType.WORD,
                "original/3.docx", null, "parsed/3/content.md", null, "text/markdown", "# 第二章\n正文",
                null, null)).headings()).singleElement().satisfies(heading -> {
                    assertThat(heading.level()).isEqualTo(1);
                    assertThat(heading.source()).isEqualTo(HeadingEvidenceSource.LLM);
                    assertThat(heading.lineNumber()).isEqualTo(1);
                });
    }

    private DocumentStructureResolver resolver(FileStorageService storageService) {
        return resolver(storageService, new PassthroughHeadingHierarchyRefiner());
    }

    private DocumentStructureResolver resolver(FileStorageService storageService, HeadingHierarchyRefiner refiner) {
        DocumentStructureProperties structureProperties = new DocumentStructureProperties();
        ArtifactProcessingProperties artifactProperties = new ArtifactProcessingProperties();
        artifactProperties.setMaxWorkspaceBytes(1024 * 1024L);
        HeadingNumberingParser numberingParser = new HeadingNumberingParser();
        return new DocumentStructureResolver(
                new MarkdownHeadingEvidenceExtractor(), new HeadingHierarchyResolver(structureProperties),
                new HeadingLineLocator(), new WordHeadingEvidenceExtractor(numberingParser),
                new MinerUHeadingEvidenceExtractor(), new MinerUContentListHeadingEvidenceExtractor(), storageService,
                new PdfOutlineHeadingEvidenceExtractor(new BoundedFileTransfer(), artifactProperties),
                new PdfLayoutHeadingEvidenceExtractor(), numberingParser, refiner);
    }

    private byte[] createOutlinedPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDDocumentOutline outline = new PDDocumentOutline();
            document.getDocumentCatalog().setDocumentOutline(outline);
            PDOutlineItem item = new PDOutlineItem();
            item.setTitle("第一章");
            item.setDestination(page);
            outline.addLast(item);
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createPdfWithoutOutline() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
