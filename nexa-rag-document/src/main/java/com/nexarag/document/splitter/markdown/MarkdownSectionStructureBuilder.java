package com.nexarag.document.splitter.markdown;

import com.nexarag.document.dto.MarkdownSplitOptions;
import com.nexarag.document.dto.SplitConfigRequest;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.splitter.ChunkDraft;
import com.nexarag.document.splitter.DocumentChunkIdGenerator;
import com.nexarag.document.splitter.DocumentSectionDraft;
import com.nexarag.document.splitter.DocumentSplitContext;
import com.nexarag.document.splitter.DocumentSplitResult;
import com.nexarag.document.splitter.support.TextWindowRange;
import com.nexarag.document.splitter.support.TextWindowSplitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Markdown 章节结构构建器，统一将标题树转换为章节草稿与可检索正文片段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkdownSectionStructureBuilder {

    private final MarkdownHeadingScanner headingScanner;
    private final TextWindowSplitter textWindowSplitter;
    private final DocumentChunkIdGenerator chunkIdGenerator;

    /**
     * 构建 Markdown 章节与片段；标题树不可信时自动降级为非结构化窗口片段。
     *
     * @param context  文档切分上下文
     * @param strategy Markdown 切分策略
     * @return 文档切分结果
     */
    public DocumentSplitResult build(DocumentSplitContext context, SplitStrategy strategy) {
        try {
            // 1. 解析可信的标题树并生成章节草稿
            List<MarkdownSection> sections = headingScanner.scan(context.content(), context.config().markdown(), context.documentId());
            List<DocumentSectionDraft> sectionDrafts = sections.stream()
                    .map(section -> new DocumentSectionDraft(section.sectionId(), section.parentSectionId(), section.title(),
                            section.headingPath(), section.level(), section.startLine(), section.endLine()))
                    .toList();

            // 2. 仅为直属正文非空的章节生成片段
            List<ChunkDraft> chunks = new ArrayList<>();
            for (MarkdownSection section : sections) {
                if (StringUtils.hasText(section.bodyText())) {
                    appendSectionChunks(chunks, context, strategy, section);
                }
            }
            return new DocumentSplitResult(sectionDrafts, chunks, true);
        } catch (MarkdownStructureException exception) {
            log.warn("Markdown标题树不可用，降级为非结构化切分，documentId={}，reason={}",
                    context.documentId(), exception.getMessage());
            return fallbackToUnstructuredChunks(context, strategy);
        }
    }

    private void appendSectionChunks(List<ChunkDraft> chunks,
                                     DocumentSplitContext context,
                                     SplitStrategy strategy,
                                     MarkdownSection section) {
        SplitConfigRequest config = context.config();
        String bodyText = section.bodyText();
        String sectionText = shouldKeepHeaders(config) ? headingText(section, bodyText) : bodyText;
        if (sectionText.length() <= config.chunkSize()) {
            chunks.add(newChunk(context, strategy, section, null, sectionText, bodyText, false, null));
            return;
        }

        boolean createParent = strategy == SplitStrategy.PARENT_MARKDOWN
                && (config.markdown() == null || !Boolean.FALSE.equals(config.markdown().createParentForOversized()));
        String parentChunkId = createParent ? chunkIdGenerator.nextChunkId(context.documentId()) : null;
        if (createParent) {
            chunks.add(new ChunkDraft(parentChunkId, null, section.sectionId(), sectionText, indexContent(context, section, bodyText),
                    null, metadata(context, strategy, section, true, null), true));
        }
        int bodyStartOffset = sectionText.length() - bodyText.length();
        List<TextWindowRange> rawWindows = textWindowSplitter.splitRanges(sectionText, config.chunkSize(), config.chunkOverlap());
        for (int i = 0; i < rawWindows.size(); i++) {
            TextWindowRange rawWindow = rawWindows.get(i);
            int indexStartOffset = Math.max(rawWindow.startOffset(), bodyStartOffset);
            if (indexStartOffset >= rawWindow.endOffset()) {
                continue;
            }
            String rawText = sectionText.substring(rawWindow.startOffset(), rawWindow.endOffset());
            String indexText = sectionText.substring(indexStartOffset, rawWindow.endOffset());
            if (StringUtils.hasText(indexText)) {
                chunks.add(newChunk(context, strategy, section, parentChunkId, rawText, indexText, false, i));
            }
        }
    }

    private ChunkDraft newChunk(DocumentSplitContext context,
                                SplitStrategy strategy,
                                MarkdownSection section,
                                String parentChunkId,
                                String text,
                                String indexText,
                                boolean parent,
                                Integer chunkIndex) {
        return new ChunkDraft(chunkIdGenerator.nextChunkId(context.documentId()), parentChunkId, section.sectionId(), text,
                indexContent(context, section, indexText), null, metadata(context, strategy, section, parent, chunkIndex), false);
    }

    private boolean shouldKeepHeaders(SplitConfigRequest config) {
        MarkdownSplitOptions markdown = config.markdown();
        return markdown == null || !Boolean.TRUE.equals(markdown.stripHeaders());
    }

    private String headingText(MarkdownSection section, String bodyText) {
        return "#".repeat(section.level()) + " " + section.title() + "\n" + bodyText;
    }

    private DocumentSplitResult fallbackToUnstructuredChunks(DocumentSplitContext context, SplitStrategy strategy) {
        SplitConfigRequest config = context.config();
        List<ChunkDraft> chunks = textWindowSplitter.split(context.content(), config.chunkSize(), config.chunkOverlap()).stream()
                .map(text -> new ChunkDraft(chunkIdGenerator.nextChunkId(context.documentId()), null, null, text, text,
                        null, fallbackMetadata(context, strategy), false))
                .toList();
        return DocumentSplitResult.unstructured(chunks);
    }

    private String indexContent(DocumentSplitContext context, MarkdownSection section, String text) {
        String documentTitle = context.title() == null ? "" : context.title();
        return documentTitle + "\n" + String.join(" > ", section.headingPath()) + "\n" + text;
    }

    private Map<String, Object> metadata(DocumentSplitContext context,
                                         SplitStrategy strategy,
                                         MarkdownSection section,
                                         boolean parent,
                                         Integer chunkIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("splitStrategy", strategy.name());
        metadata.put("fileType", context.fileType().name());
        metadata.put("title", section.title());
        metadata.put("titleLevel", section.level());
        metadata.put("titlePath", section.headingPath());
        metadata.put("startLine", section.startLine());
        metadata.put("endLine", section.endLine());
        if (strategy == SplitStrategy.PARENT_MARKDOWN) {
            metadata.put("parent", parent);
            if (chunkIndex != null) {
                metadata.put("childIndex", chunkIndex);
            }
        } else if (chunkIndex != null) {
            metadata.put("brotherIndex", chunkIndex);
        }
        return metadata;
    }

    private Map<String, Object> fallbackMetadata(DocumentSplitContext context, SplitStrategy strategy) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("splitStrategy", strategy.name());
        metadata.put("fileType", context.fileType().name());
        metadata.put("structuredFallback", true);
        return metadata;
    }
}
