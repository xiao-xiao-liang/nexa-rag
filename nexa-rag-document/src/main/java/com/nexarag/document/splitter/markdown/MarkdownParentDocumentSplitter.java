package com.nexarag.document.splitter.markdown;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.dto.MarkdownSplitOptions;
import com.nexarag.document.dto.SplitConfigRequest;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.error.DocumentErrorCode;
import com.nexarag.document.splitter.ChunkDraft;
import com.nexarag.document.splitter.DocumentChunkIdGenerator;
import com.nexarag.document.splitter.DocumentSplitContext;
import com.nexarag.document.splitter.DocumentSplitter;
import com.nexarag.document.splitter.support.TextWindowSplitter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Markdown 父子切分器，负责生成可跳过索引的父片段和可索引的子片段。
 */
@Component
@RequiredArgsConstructor
public class MarkdownParentDocumentSplitter implements DocumentSplitter {

    private final MarkdownHeadingScanner headingScanner;
    private final TextWindowSplitter textWindowSplitter;
    private final DocumentChunkIdGenerator chunkIdGenerator;

    @Override
    public SplitStrategy strategy() {
        return SplitStrategy.PARENT_MARKDOWN;
    }

    /**
     * 按 Markdown 标题和窗口大小切分文档。
     *
     * @param context 文档切分上下文
     * @return 片段草稿列表
     */
    @Override
    public List<ChunkDraft> split(DocumentSplitContext context) {
        validateContext(context);
        SplitConfigRequest config = context.config();
        MarkdownSplitOptions options = config.markdown();
        boolean createParent = options == null || !Boolean.FALSE.equals(options.createParentForOversized());
        List<ChunkDraft> drafts = new ArrayList<>();

        // 1. 先按标题区块切分，再对超长区块做父子拆分
        List<MarkdownSection> sections = headingScanner.scan(context.content(), options);
        for (MarkdownSection section : sections) {
            if (section.text().length() <= config.chunkSize()) {
                drafts.add(new ChunkDraft(chunkIdGenerator.nextChunkId(context.documentId()), null, section.text(), null,
                        metadata(context, section, false, null), false));
                continue;
            }

            String parentChunkId = createParent ? chunkIdGenerator.nextChunkId(context.documentId()) : null;
            if (createParent) {
                drafts.add(new ChunkDraft(parentChunkId, null, section.text(), null,
                        metadata(context, section, true, null), true));
            }
            List<String> children = textWindowSplitter.split(section.text(), config.chunkSize(), config.chunkOverlap());
            for (int i = 0; i < children.size(); i++) {
                Map<String, Object> metadata = metadata(context, section, false, i);
                drafts.add(new ChunkDraft(chunkIdGenerator.nextChunkId(context.documentId()), parentChunkId,
                        children.get(i), null, metadata, false));
            }
        }
        return drafts;
    }

    private void validateContext(DocumentSplitContext context) {
        if (context == null || !StringUtils.hasText(context.content()) || context.config() == null) {
            throw new ServiceException("Markdown切分上下文不完整", DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }

    private Map<String, Object> metadata(DocumentSplitContext context,
                                         MarkdownSection section,
                                         boolean parent,
                                         Integer childIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("splitStrategy", strategy().name());
        metadata.put("fileType", context.fileType().name());
        metadata.put("title", section.title());
        metadata.put("titleLevel", section.level());
        metadata.put("titlePath", section.titlePath());
        metadata.put("startLine", section.startLine());
        metadata.put("endLine", section.endLine());
        metadata.put("parent", parent);
        if (childIndex != null) {
            metadata.put("childIndex", childIndex);
        }
        return metadata;
    }
}
