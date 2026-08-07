package com.nexarag.document.splitter;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.enums.DocumentErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 文档切分器工厂。
 */
@Component
public class DocumentSplitterFactory {

    private final Map<SplitStrategy, DocumentSplitter> splitterMap = new EnumMap<>(SplitStrategy.class);

    public DocumentSplitterFactory(List<DocumentSplitter> splitters) {
        for (DocumentSplitter splitter : splitters) {
            splitterMap.put(splitter.strategy(), splitter);
        }
    }

    /**
     * 根据策略获取切分器。
     *
     * @param strategy 切分策略
     * @return 文档切分器
     */
    public DocumentSplitter getRequired(SplitStrategy strategy) {
        DocumentSplitter splitter = splitterMap.get(strategy);
        if (splitter == null) {
            throw new ServiceException("未找到文档切分器: " + strategy, DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
        return splitter;
    }
}
