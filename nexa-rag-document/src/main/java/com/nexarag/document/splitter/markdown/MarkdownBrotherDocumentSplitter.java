package com.nexarag.document.splitter.markdown;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.splitter.DocumentChunkIdGenerator;
import com.nexarag.document.splitter.DocumentSplitContext;
import com.nexarag.document.splitter.DocumentSplitResult;
import com.nexarag.document.splitter.DocumentSplitter;
import com.nexarag.document.splitter.support.TextWindowSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
/**
 * Markdown 同级切分器，负责按标题区块生成同级可索引片段。
 */
@Component
public class MarkdownBrotherDocumentSplitter implements DocumentSplitter {

    private final MarkdownSectionStructureBuilder structureBuilder;

    /**
     * 创建 Markdown 同级切分器。
     *
     * @param structureBuilder Markdown 章节结构构建器
     */
    @Autowired
    public MarkdownBrotherDocumentSplitter(MarkdownSectionStructureBuilder structureBuilder) {
        this.structureBuilder = structureBuilder;
    }

    /**
     * 兼容直接构造切分器的测试和调用方式。
     *
     * @param headingScanner      Markdown 标题扫描器
     * @param textWindowSplitter  文本窗口切分器
     * @param chunkIdGenerator    片段ID生成器
     */
    public MarkdownBrotherDocumentSplitter(MarkdownHeadingScanner headingScanner,
                                           TextWindowSplitter textWindowSplitter,
                                           DocumentChunkIdGenerator chunkIdGenerator) {
        this(new MarkdownSectionStructureBuilder(headingScanner, textWindowSplitter, chunkIdGenerator));
    }

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
                    com.nexarag.document.enums.DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }
}
