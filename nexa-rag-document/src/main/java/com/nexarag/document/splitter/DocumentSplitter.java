package com.nexarag.document.splitter;

import com.nexarag.document.dto.SplitConfigRequest;
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
     * 切分文本内容。
     *
     * @param content 文本内容
     * @param config  切分配置
     * @return 片段草稿列表
     */
    List<ChunkDraft> split(String content, SplitConfigRequest config);
}
