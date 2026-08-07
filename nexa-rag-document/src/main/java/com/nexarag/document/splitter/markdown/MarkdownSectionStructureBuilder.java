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
            List<MarkdownSection> sections = headingScanner.scan(
                    context.content(),
                    context.config().markdown(),
                    context.documentId()
            );
            List<DocumentSectionDraft> sectionDrafts = sections.stream()
                    .map(section -> new DocumentSectionDraft(
                            section.sectionId(),
                            section.parentSectionId(),
                            section.title(),
                            section.headingPath(),
                            section.level(),
                            section.startLine(),
                            section.endLine()
                    ))
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
            log.warn("Markdown标题树不可用，降级为非结构化切分，documentId={}，reason={}", context.documentId(), exception.getMessage());
            return fallbackToUnstructuredChunks(context, strategy);
        }
    }

    /**
     * 为指定 Markdown 章节生成片段。
     */
    private void appendSectionChunks(List<ChunkDraft> chunks, DocumentSplitContext context, SplitStrategy strategy, MarkdownSection section) {
        SplitConfigRequest config = context.config();
        String bodyText = section.bodyText();
        String sectionText = shouldKeepHeaders(config) ? headingText(section, bodyText) : bodyText;

        // 章节整体未超过 chunkSize 直接作为一个普通片段
        if (sectionText.length() <= config.chunkSize()) {
            chunks.add(newChunk(context, strategy, section, null, sectionText, bodyText, null));
            return;
        }

        boolean createParent = strategy == SplitStrategy.PARENT_MARKDOWN
                && (config.markdown() == null || !Boolean.FALSE.equals(config.markdown().createParentForOversized()));
        String parentChunkId = createParent ? chunkIdGenerator.nextChunkId(context.documentId()) : null;
        if (createParent) {
            chunks.add(ChunkDraft.builder()
                            .chunkId(parentChunkId)
                            .sectionId(section.sectionId())
                            .text(sectionText)
                            .indexContent(indexContent(context, section, bodyText))
                            .metadata(metadata(context, strategy, section, true, null))
                            .skipIndex(true)
                            .build());
        }
        
        // 如果保留了 Markdown 标题，sectionText 前部会比 bodyText 多出标题文本
        // 计算正文在 sectionText 中真正开始的位置，避免标题自身成为独立索引内容
        int bodyStartOffset = sectionText.length() - bodyText.length();
        List<TextWindowRange> rawWindows = textWindowSplitter.splitRanges(sectionText, config.chunkSize(), config.chunkOverlap());
        for (int i = 0; i < rawWindows.size(); i++) {
            TextWindowRange rawWindow = rawWindows.get(i);
            int indexStartOffset = Math.max(rawWindow.startOffset(), bodyStartOffset);
            
            // 当前窗口只有标题，没有正文，不生成可索引子片段
            if (indexStartOffset >= rawWindow.endOffset()) {
                continue;
            }
            
            String rawText = sectionText.substring(rawWindow.startOffset(), rawWindow.endOffset());
            String indexText = sectionText.substring(indexStartOffset, rawWindow.endOffset());
            if (StringUtils.hasText(indexText)) {
                chunks.add(newChunk(context, strategy, section, parentChunkId, rawText, indexText, i));
            }
        }
    }

    /**
     * 创建普通可索引片段。
     */
    private ChunkDraft newChunk(DocumentSplitContext context, SplitStrategy strategy,
                                MarkdownSection section, String parentChunkId, String text,
                                String indexText, Integer chunkIndex) {
        return ChunkDraft.builder()
                .chunkId(chunkIdGenerator.nextChunkId(context.documentId()))
                .parentChunkId(parentChunkId)
                .sectionId(section.sectionId())
                .text(text)
                .indexContent(indexContent(context, section, indexText))
                .metadata(metadata(context, strategy, section, false, chunkIndex))
                .skipIndex(false)
                .build();
    }

    /**
     * 是否在片段文本中保留 Markdown 标题。
     */
    private boolean shouldKeepHeaders(SplitConfigRequest config) {
        MarkdownSplitOptions markdown = config.markdown();
        return markdown == null || !Boolean.TRUE.equals(markdown.stripHeaders());
    }

    /**
     * 构造包含 Markdown 标题的章节文本。
     */
    private String headingText(MarkdownSection section, String bodyText) {
        return "#".repeat(section.level()) + " " + section.title() + "\n" + bodyText;
    }

    /**
     * Markdown 结构解析失败时，降级为普通窗口切分。
     */
    private DocumentSplitResult fallbackToUnstructuredChunks(DocumentSplitContext context, SplitStrategy strategy) {
        SplitConfigRequest config = context.config();
        List<ChunkDraft> chunks = textWindowSplitter.split(context.content(), config.chunkSize(), config.chunkOverlap())
                        .stream()
                        .map(text -> ChunkDraft.builder()
                                        .chunkId(chunkIdGenerator.nextChunkId(context.documentId()))
                                        .text(text)
                                        .indexContent(text)
                                        .metadata(fallbackMetadata(context, strategy))
                                        .skipIndex(false)
                                        .build())
                        .toList();
        return DocumentSplitResult.unstructured(chunks);
    }

    private String indexContent(DocumentSplitContext context, MarkdownSection section, String text) {
        String documentTitle = context.title() == null ? "" : context.title();
        return documentTitle + "\n" + String.join(" > ", section.headingPath()) + "\n" + text;
    }

    private Map<String, Object> metadata(DocumentSplitContext context, SplitStrategy strategy, MarkdownSection section,
                                         boolean parent, Integer chunkIndex) {
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
