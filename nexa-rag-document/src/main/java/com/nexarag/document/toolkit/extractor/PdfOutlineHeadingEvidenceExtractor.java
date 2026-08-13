package com.nexarag.document.toolkit.extractor;

import com.nexarag.document.enums.HeadingEvidenceSource;
import com.nexarag.document.model.bo.structure.HeadingEvidenceBO;
import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.parser.workspace.BoundedFileTransfer;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 书签标题证据提取器。
 *
 * <p>PDFBox 需要随机访问输入，因此先将对象存储输入流有上限地复制到临时文件，
 * 避免将整个 PDF 读入 JVM 堆。</p>
 */
@Component
@RequiredArgsConstructor
public class PdfOutlineHeadingEvidenceExtractor {
    private final BoundedFileTransfer boundedFileTransfer;
    private final ArtifactProcessingProperties artifactProcessingProperties;

    /**
     * 提取 PDF Outline 的标题、层级和目标页码。
     *
     * @param inputStream 原始 PDF 输入流
     * @return 按书签阅读顺序排列的标题证据
     */
    public List<HeadingEvidenceBO> extract(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return List.of();
        }
        Path temporaryRoot = artifactProcessingProperties.getTempRoot();
        Files.createDirectories(temporaryRoot);
        Path temporaryFile = Files.createTempFile(temporaryRoot, "nexa-rag-pdf-outline-", ".pdf");
        try {
            long maxBytes = artifactProcessingProperties.getMaxWorkspaceBytes();
            if (maxBytes <= 0) {
                throw new IOException("PDF结构提取文件大小限制必须大于零");
            }
            // BoundedFileTransfer 使用 CREATE_NEW 写入，先释放本方法创建的唯一临时路径。
            Files.deleteIfExists(temporaryFile);
            boundedFileTransfer.copy(inputStream, temporaryFile, maxBytes);
            try (PDDocument document = Loader.loadPDF(temporaryFile.toFile())) {
                PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
                if (outline == null) {
                    return List.of();
                }
                List<HeadingEvidenceBO> evidences = new ArrayList<>();
                collect(outline.children(), document, 1, evidences);
                return List.copyOf(evidences);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private void collect(Iterable<PDOutlineItem> items, PDDocument document, int level,
                         List<HeadingEvidenceBO> evidences) throws IOException {
        for (PDOutlineItem item : items) {
            String title = item.getTitle() == null ? "" : item.getTitle().strip();
            if (StringUtils.hasText(title)) {
                evidences.add(new HeadingEvidenceBO(title, Math.min(level, 6), evidences.size() + 1,
                        HeadingEvidenceSource.PDF_OUTLINE, 1.0D, resolvePageNumber(item, document)));
            }
            collect(item.children(), document, level + 1, evidences);
        }
    }

    private Integer resolvePageNumber(PDOutlineItem item, PDDocument document) throws IOException {
        PDPage page = item.findDestinationPage(document);
        if (page == null) {
            return null;
        }
        int pageIndex = document.getPages().indexOf(page);
        return pageIndex < 0 ? null : pageIndex + 1;
    }
}
