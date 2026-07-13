package com.nexarag.retrieval.index.keyword;

import com.nexarag.retrieval.dto.req.KeywordIndexWriteRequest;
import com.nexarag.retrieval.model.KeywordIndexDocument;
import com.nexarag.retrieval.model.KeywordIndexWriteResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 空关键词索引客户端测试，验证关闭关键词中间件时不会访问外部索引。
 */
class NoneKeywordIndexClientTest {

    @Test
    void upsertShouldReturnStableSuccessResultWithoutExternalWrite() {
        NoneKeywordIndexClient client = new NoneKeywordIndexClient();
        KeywordIndexWriteRequest request = new KeywordIndexWriteRequest("nexa_document_chunk", 1L,
                List.of(new KeywordIndexDocument("chunk-1", 1L, null, 0, "测试文本", "{}")));

        List<KeywordIndexWriteResult> results = client.upsert(request);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().success()).isTrue();
        assertThat(results.getFirst().chunkId()).isEqualTo("chunk-1");
        assertThat(results.getFirst().keywordIndexId()).isEqualTo("none:chunk-1");
    }

    @Test
    void deleteByDocumentIdShouldReturnZero() {
        NoneKeywordIndexClient client = new NoneKeywordIndexClient();

        int deletedCount = client.deleteByDocumentId(1L);

        assertThat(deletedCount).isZero();
    }
}
