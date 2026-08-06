package com.nexarag.document.splitter.markdown;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.dto.SplitConfigRequest;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.error.DocumentErrorCode;
import com.nexarag.document.splitter.DocumentChunkIdGenerator;
import com.nexarag.document.splitter.DocumentSplitContext;
import com.nexarag.document.splitter.DocumentSplitResult;
import com.nexarag.document.splitter.DocumentSplitter;
import com.nexarag.document.splitter.support.TextWindowSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Markdown 父子切分器，负责生成可跳过索引的父片段和可索引的子片段。
 */
@Component
public class MarkdownParentDocumentSplitter implements DocumentSplitter {

    private final MarkdownSectionStructureBuilder structureBuilder;

    /**
     * 创建 Markdown 父子切分器。
     *
     * @param structureBuilder Markdown 章节结构构建器
     */
    @Autowired
    public MarkdownParentDocumentSplitter(MarkdownSectionStructureBuilder structureBuilder) {
        this.structureBuilder = structureBuilder;
    }

    /**
     * 兼容直接构造切分器的测试和调用方式。
     *
     * @param headingScanner      Markdown 标题扫描器
     * @param textWindowSplitter  文本窗口切分器
     * @param chunkIdGenerator    片段ID生成器
     */
    public MarkdownParentDocumentSplitter(MarkdownHeadingScanner headingScanner,
                                          TextWindowSplitter textWindowSplitter,
                                          DocumentChunkIdGenerator chunkIdGenerator) {
        this(new MarkdownSectionStructureBuilder(headingScanner, textWindowSplitter, chunkIdGenerator));
    }

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
