package com.nexarag.document.splitter.markdown;

import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.model.bo.split.DocumentSplitContext;
import com.nexarag.document.model.bo.split.DocumentSplitResult;
import com.nexarag.document.splitter.DocumentSplitter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Markdown 同级切分器，负责按标题区块生成同级可索引片段。
 */
@Component
@RequiredArgsConstructor
public class MarkdownBrotherDocumentSplitter implements DocumentSplitter {

    private final MarkdownSectionStructureBuilder structureBuilder;

    @Override
    public SplitStrategy strategy() {
        return SplitStrategy.BROTHER_MARKDOWN;
    }

    /**
     * 按 Markdown 标题生成同级片段。
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
        if (context == null || !org.springframework.util.StringUtils.hasText(context.content()) || context.config() == null) {
            throw new com.nexarag.common.exception.ServiceException("Markdown同级切分上下文不完整",
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }
}
