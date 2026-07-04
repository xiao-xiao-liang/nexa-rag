package com.nexarag.document.service;

import com.nexarag.document.dto.IndexConfigRequest;
import com.nexarag.document.dto.ParseConfigRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.dto.SplitConfigRequest;
import com.nexarag.document.dto.UploadDocumentRequest;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.infra.parser.ParserType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档处理配置默认值测试。
 */
class ProcessConfigDefaultsTest {

    private final ProcessConfigDefaults defaults = new ProcessConfigDefaults();

    @Test
    void mergeShouldUsePdfDefaultsWhenNestedConfigIsNull() {
        ProcessDocumentRequest result = defaults.merge(FileType.PDF,
                new UploadDocumentRequest("测试文档", null, null, null, null));

        assertThat(result.splitConfig().splitStrategy()).isEqualTo(SplitStrategy.PARENT_MARKDOWN);
        assertThat(result.splitConfig().chunkSize()).isEqualTo(1000);
        assertThat(result.splitConfig().chunkOverlap()).isEqualTo(100);
        assertThat(result.parseConfig().parserType()).isEqualTo(ParserType.MINERU);
        assertThat(result.indexConfig().enabled()).isTrue();
        assertThat(result.indexConfig().vectorEnabled()).isTrue();
        assertThat(result.indexConfig().keywordEnabled()).isTrue();
    }

    @Test
    void mergeShouldKeepExplicitNestedConfig() {
        SplitConfigRequest splitConfig = new SplitConfigRequest(SplitStrategy.REGEX_TEXT, 500, 20);
        ParseConfigRequest parseConfig = new ParseConfigRequest(ParserType.TIKA, false, false);
        IndexConfigRequest indexConfig = new IndexConfigRequest(false, false, true);

        ProcessDocumentRequest result = defaults.merge(FileType.TEXT,
                new UploadDocumentRequest("测试文档", null, splitConfig, parseConfig, indexConfig));

        assertThat(result.splitConfig()).isSameAs(splitConfig);
        assertThat(result.parseConfig()).isSameAs(parseConfig);
        assertThat(result.indexConfig()).isSameAs(indexConfig);
    }
}
