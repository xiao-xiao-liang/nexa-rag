package com.nexarag.retrieval.index.keyword;

import com.nexarag.retrieval.dto.req.KeywordIndexSearchRequest;
import com.nexarag.retrieval.dto.req.KeywordIndexWriteRequest;
import com.nexarag.retrieval.model.KeywordIndexSearchResult;
import com.nexarag.retrieval.model.KeywordIndexWriteResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 空关键词索引客户端，用于 keyword.type=none 时跳过外部关键词中间件写入。
 */
@Component
@ConditionalOnProperty(prefix = "nexa.retrieval.keyword", name = "type", havingValue = "none", matchIfMissing = true)
public class NoneKeywordIndexClient implements KeywordIndexClient {

    @Override
    public List<KeywordIndexSearchResult> search(KeywordIndexSearchRequest request) {
        return List.of();
    }

    /**
     * 返回稳定成功结果，不访问外部关键词索引服务。
     *
     * @param request 关键词索引写入请求
     * @return 写入结果列表
     */
    @Override
    public List<KeywordIndexWriteResult> upsert(KeywordIndexWriteRequest request) {
        if (request == null || request.documents() == null || request.documents().isEmpty()) {
            return List.of();
        }

        // 1. 为每个片段返回稳定的空索引标识，避免上层结果聚合出现空值
        return request.documents().stream()
                .map(document -> new KeywordIndexWriteResult(document.chunkId(), "none:" + document.chunkId(),
                        true, null))
                .toList();
    }

}
