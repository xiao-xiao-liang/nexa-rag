package com.nexarag.document.splitter.markdown;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.model.bo.split.DocumentSplitContext;
import com.nexarag.document.model.bo.split.DocumentSplitResult;
import com.nexarag.document.splitter.DocumentSplitter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Markdown 父子切分器，负责生成可跳过索引的父片段和可索引的子片段。
 */
@Component
@RequiredArgsConstructor
public class MarkdownParentDocumentSplitter implements DocumentSplitter {

    private final MarkdownSectionStructureBuilder structureBuilder;

    @Override
    public SplitStrategy strategy() {
        return SplitStrategy.PARENT_MARKDOWN;
    }

    /**
     * 按 Markdown 标题和窗口大小切分文档。
     *
     * @param context 文档切分上下文
     * @return 文档切分结果
     */
    @Override
    public DocumentSplitResult split(DocumentSplitContext context) {
        validateContext(context);
        return structureBuilder.build(context, strategy());
    }

    private void validateContext(DocumentSplitContext context) {
        if (context == null || !StringUtils.hasText(context.content()) || context.config() == null) {
            throw new ServiceException("Markdown切分上下文不完整", DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }
}
