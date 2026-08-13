package com.nexarag.document.toolkit.resolver;

import com.nexarag.document.model.bo.structure.DocumentStructureBO;
import com.nexarag.document.model.bo.structure.HeadingEvidenceBO;
import com.nexarag.document.enums.HeadingEvidenceSource;
import com.nexarag.document.model.bo.split.DocumentSplitContext;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.toolkit.extractor.*;
import com.nexarag.document.toolkit.refiner.HeadingHierarchyRefiner;
import com.nexarag.infra.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文档结构解析编排器。
 *
 * <p>阶段一首先以 Markdown 标题为可信基线；DOCX 和 MinerU 附属制品已通过上下文传入，
 * 后续证据提取器可在不改变切分器 API 的前提下接入。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentStructureResolver {
    private final MarkdownHeadingEvidenceExtractor markdownHeadingEvidenceExtractor;
    private final HeadingHierarchyResolver headingHierarchyResolver;
    private final HeadingLineLocator headingLineLocator;
    private final WordHeadingEvidenceExtractor wordHeadingEvidenceExtractor;
    private final MinerUHeadingEvidenceExtractor minerUHeadingEvidenceExtractor;
    private final MinerUContentListHeadingEvidenceExtractor minerUContentListHeadingEvidenceExtractor;
    private final FileStorageService fileStorageService;
    private final PdfOutlineHeadingEvidenceExtractor pdfOutlineHeadingEvidenceExtractor;
    private final PdfLayoutHeadingEvidenceExtractor pdfLayoutHeadingEvidenceExtractor;
    private final HeadingNumberingParser headingNumberingParser;
    private final HeadingHierarchyRefiner headingHierarchyRefiner;

    /** 解析当前切分上下文的可信标题。 */
    public DocumentStructureBO resolve(DocumentSplitContext context) {
        List<HeadingEvidenceBO> markdownEvidences = markdownHeadingEvidenceExtractor.extract(context.content());
        List<HeadingEvidenceBO> evidences = new ArrayList<>(markdownEvidences);
        if (context.fileType() == FileType.WORD) {
            addWordEvidences(context, markdownEvidences, evidences);
        } else if (context.fileType() == FileType.PDF) {
            addPdfEvidences(context, markdownEvidences, evidences);
        }
        List<HeadingEvidenceBO> headings = headingHierarchyResolver.resolve(evidences);
        return headingLineLocator.locate(context.content(), headingHierarchyRefiner.refine(context.documentId(), headings));
    }

    private void addWordEvidences(DocumentSplitContext context, List<HeadingEvidenceBO> markdownEvidences,
                                  List<HeadingEvidenceBO> evidences) {
        try (InputStream inputStream = fileStorageService.load(context.originalObjectName())) {
            evidences.addAll(alignSequences(wordHeadingEvidenceExtractor.extract(inputStream), markdownEvidences));
        } catch (Exception exception) {
            log.warn("读取DOCX标题结构证据失败，documentId={}", context.documentId(), exception);
        }
    }

    private void addPdfEvidences(DocumentSplitContext context, List<HeadingEvidenceBO> markdownEvidences,
                                 List<HeadingEvidenceBO> evidences) {
        try (InputStream inputStream = fileStorageService.load(context.originalObjectName())) {
            evidences.addAll(alignSequences(pdfOutlineHeadingEvidenceExtractor.extract(inputStream), markdownEvidences));
        } catch (Exception exception) {
            log.warn("读取PDF书签结构证据失败，documentId={}", context.documentId(), exception);
        }
        addMineruContentListLayoutEvidences(context, markdownEvidences, evidences);
        context.structureArtifacts().stream()
                .filter(artifact -> "MINERU_MIDDLE_JSON".equals(artifact.type()))
                .findFirst()
                .ifPresent(artifact -> {
                    try (InputStream inputStream = fileStorageService.load(artifact.objectKey())) {
                        List<HeadingEvidenceBO> mineruEvidences = minerUHeadingEvidenceExtractor.extract(inputStream);
                        evidences.addAll(alignSequences(toPdfNumberingEvidences(mineruEvidences), markdownEvidences));
                    } catch (Exception exception) {
                        log.warn("读取MinerU标题结构证据失败，documentId={}", context.documentId(), exception);
                    }
                });
        context.structureArtifacts().stream()
                .filter(artifact -> "MINERU_MIDDLE_JSON".equals(artifact.type()))
                .findFirst()
                .ifPresent(artifact -> {
                    try (InputStream inputStream = fileStorageService.load(artifact.objectKey())) {
                        evidences.addAll(alignSequences(pdfLayoutHeadingEvidenceExtractor.extract(inputStream), markdownEvidences));
                    } catch (Exception exception) {
                        log.warn("读取MinerU版式结构证据失败，documentId={}", context.documentId(), exception);
                    }
                });
    }

    /** 优先使用官方 V2 Content List，V2 缺失时回退至旧版 Content List。 */
    private void addMineruContentListLayoutEvidences(DocumentSplitContext context,
                                                      List<HeadingEvidenceBO> markdownEvidences,
                                                      List<HeadingEvidenceBO> evidences) {
        context.structureArtifacts().stream()
                .filter(artifact -> "MINERU_CONTENT_LIST_V2_JSON".equals(artifact.type()))
                .findFirst()
                .ifPresentOrElse(artifact -> addMineruContentListV2Evidences(context, artifact.objectKey(),
                                markdownEvidences, evidences),
                        () -> context.structureArtifacts().stream()
                                .filter(artifact -> "MINERU_CONTENT_LIST_JSON".equals(artifact.type()))
                                .findFirst()
                                .ifPresent(artifact -> addMineruContentListLegacyEvidences(context,
                                        artifact.objectKey(), markdownEvidences, evidences)));
    }

    /** 读取官方 V2 Content List 的相对字号标题证据。 */
    private void addMineruContentListV2Evidences(DocumentSplitContext context, String objectKey,
                                                  List<HeadingEvidenceBO> markdownEvidences,
                                                  List<HeadingEvidenceBO> evidences) {
        try (InputStream inputStream = fileStorageService.load(objectKey)) {
            addOfficialMineruLayoutEvidences(minerUContentListHeadingEvidenceExtractor.extractV2(inputStream),
                    markdownEvidences, evidences);
        } catch (Exception exception) {
            log.warn("读取MinerU V2 Content List标题结构证据失败，documentId={}", context.documentId(), exception);
        }
    }

    /** 读取旧版 Content List 的相对字号标题证据。 */
    private void addMineruContentListLegacyEvidences(DocumentSplitContext context, String objectKey,
                                                      List<HeadingEvidenceBO> markdownEvidences,
                                                      List<HeadingEvidenceBO> evidences) {
        try (InputStream inputStream = fileStorageService.load(objectKey)) {
            addOfficialMineruLayoutEvidences(minerUContentListHeadingEvidenceExtractor.extractLegacy(inputStream),
                    markdownEvidences, evidences);
        } catch (Exception exception) {
            log.warn("读取MinerU Content List标题结构证据失败，documentId={}", context.documentId(), exception);
        }
    }

    /** Content List 可用时，以其标题集合过滤 Markdown 中被误标的非章节块。 */
    private void addOfficialMineruLayoutEvidences(List<HeadingEvidenceBO> contentListEvidences,
                                                   List<HeadingEvidenceBO> markdownEvidences,
                                                   List<HeadingEvidenceBO> evidences) {
        List<HeadingEvidenceBO> alignedEvidences = alignSequences(contentListEvidences, markdownEvidences);
        if (alignedEvidences.isEmpty()) {
            return;
        }
        Set<String> confirmedTitles = alignedEvidences.stream().map(HeadingEvidenceBO::title)
                .collect(Collectors.toSet());
        evidences.removeIf(evidence -> evidence.source() == HeadingEvidenceSource.MARKDOWN
                && !confirmedTitles.contains(evidence.title()));
        evidences.addAll(alignedEvidences);
    }

    private List<HeadingEvidenceBO> toPdfNumberingEvidences(List<HeadingEvidenceBO> mineruEvidences) {
        return mineruEvidences.stream().flatMap(evidence -> headingNumberingParser.parseLevel(evidence.title()).stream()
                .mapToObj(level -> new HeadingEvidenceBO(evidence.title(), level, evidence.sequence(),
                        HeadingEvidenceSource.PDF_NUMBERING, 0.90D, evidence.pageNumber()))).toList();
    }

    private List<HeadingEvidenceBO> alignSequences(List<HeadingEvidenceBO> source,
                                                    List<HeadingEvidenceBO> markdownEvidences) {
        return source.stream().map(evidence -> {
            int sequence = markdownEvidences.stream().filter(markdown -> markdown.title().equals(evidence.title()))
                    .mapToInt(HeadingEvidenceBO::sequence).findFirst().orElse(evidence.sequence());
            return new HeadingEvidenceBO(evidence.title(), evidence.declaredLevel(), sequence, evidence.source(),
                    evidence.confidence(), evidence.pageNumber());
        }).toList();
    }
}
