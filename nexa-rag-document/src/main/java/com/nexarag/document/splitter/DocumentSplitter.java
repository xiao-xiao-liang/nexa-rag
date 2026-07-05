package com.nexarag.document.splitter;

import com.nexarag.document.enums.SplitStrategy;

import java.util.List;

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
     * @return 片段草稿列表
     */
    List<ChunkDraft> split(DocumentSplitContext context);
}
