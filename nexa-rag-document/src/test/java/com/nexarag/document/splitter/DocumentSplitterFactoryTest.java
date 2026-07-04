package com.nexarag.document.splitter;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.dto.SplitConfigRequest;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.error.DocumentErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档切分器工厂测试。
 */
class DocumentSplitterFactoryTest {

    @Test
    void shouldReturnSplitterByStrategy() {
        // 1. 准备指定策略的测试切分器
        DocumentSplitter splitter = new TestDocumentSplitter(SplitStrategy.PARENT_MARKDOWN);
        DocumentSplitterFactory factory = new DocumentSplitterFactory(List.of(splitter));

        // 2. 根据策略获取切分器并校验返回同一个实例
        DocumentSplitter result = factory.getRequired(SplitStrategy.PARENT_MARKDOWN);

        assertThat(result).isSameAs(splitter);
    }

    @Test
    void shouldThrowServiceExceptionWhenSplitterMissing() {
        // 1. 准备没有任何切分器的工厂
        DocumentSplitterFactory factory = new DocumentSplitterFactory(List.of());

        // 2. 校验缺少策略实现时抛出文档处理配置错误
        assertThatThrownBy(() -> factory.getRequired(SplitStrategy.EXCEL))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID.code());
    }

    /**
     * 测试用文档切分器。
     *
     * @param strategy 切分策略
     */
    private record TestDocumentSplitter(SplitStrategy strategy) implements DocumentSplitter {

        @Override
        public List<ChunkDraft> split(String content, SplitConfigRequest config) {
            // 1. 返回最小片段草稿用于满足接口契约
            return List.of(new ChunkDraft(content, Map.of(), false));
        }
    }
}
