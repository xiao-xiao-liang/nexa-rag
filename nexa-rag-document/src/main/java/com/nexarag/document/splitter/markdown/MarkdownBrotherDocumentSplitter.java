package com.nexarag.document.splitter.markdown;

import com.nexarag.common.exception.ServiceException;
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
 * Markdown 同级切分器，负责按标题区块生成同级可索引片段。
 */
@Component
@RequiredArgsConstructor
public class MarkdownBrotherDocumentSplitter implements DocumentSplitter {

    private final MarkdownHeadingScanner headingScanner;
    private final TextWindowSplitter textWindowSplitter;
    private final DocumentChunkIdGenerator chunkIdGenerator;

    @Override
    public SplitStrategy strategy() {
        return SplitStrategy.BROTHER_MARKDOWN;
    }

    /**
     * 按 Markdown 标题生成同级片段。
     *
     * @param context 文档切分上下文
     * @return 片段草稿列表
     */
    @Override
    public List<ChunkDraft> split(DocumentSplitContext context) {
        if (context == null || !StringUtils.hasText(context.content()) || context.config() == null) {
            throw new ServiceException("Markdown同级切分上下文不完整", DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
        SplitConfigRequest config = context.config();
        List<ChunkDraft> drafts = new ArrayList<>();

        // 1. 每个标题区块独立切分，不生成父片段
        for (MarkdownSection section : headingScanner.scan(context.content(), config.markdown())) {
            List<String> chunks = section.text().length() <= config.chunkSize()
                    ? List.of(section.text())
                    : textWindowSplitter.split(section.text(), config.chunkSize(), config.chunkOverlap());
            for (int i = 0; i < chunks.size(); i++) {
                drafts.add(new ChunkDraft(chunkIdGenerator.nextChunkId(context.documentId()), null, chunks.get(i), null,
                        metadata(context, section, i), false));
            }
        }
        return drafts;
    }

    private Map<String, Object> metadata(DocumentSplitContext context, MarkdownSection section, int brotherIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("splitStrategy", strategy().name());
        metadata.put("fileType", context.fileType().name());
        metadata.put("title", section.title());
        metadata.put("titleLevel", section.level());
        metadata.put("titlePath", section.titlePath());
        metadata.put("startLine", section.startLine());
        metadata.put("endLine", section.endLine());
        metadata.put("brotherIndex", brotherIndex);
        return metadata;
    }
}
