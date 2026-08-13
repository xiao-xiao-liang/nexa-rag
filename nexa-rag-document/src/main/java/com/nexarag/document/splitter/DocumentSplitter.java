package com.nexarag.document.splitter;

import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.model.bo.split.DocumentSplitContext;
import com.nexarag.document.model.bo.split.DocumentSplitResult;

/**
 * 文档切分器接口。
 */
public interface DocumentSplitter {

    /**
     * 获取切分策略。
     *
     * @return 切分策略
     */
    SplitStrategy strategy();

    /**
     * 按上下文切分文档内容。
     *
     * @param context 文档切分上下文
     * @return 文档切分结果
     */
    DocumentSplitResult split(DocumentSplitContext context);
}
