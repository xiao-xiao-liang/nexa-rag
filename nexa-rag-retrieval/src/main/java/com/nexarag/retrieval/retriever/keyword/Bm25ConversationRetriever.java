package com.nexarag.retrieval.retriever.keyword;

import com.nexarag.retrieval.dto.req.ConversationRetrievalRequest;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.dto.req.KeywordIndexSearchRequest;
import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.model.KeywordIndexSearchResult;
import com.nexarag.retrieval.retriever.ConversationRetriever;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Elasticsearch BM25 的对话关键词检索通道。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.retrieval.keyword", name = "type", havingValue = "elasticsearch")
public class Bm25ConversationRetriever implements ConversationRetriever {

    private final KeywordIndexClient keywordIndexClient;

    @Override
    public List<RetrievalChunk> retrieve(ConversationRetrievalRequest request) {
        // 1. 调用关键词索引客户端获取 BM25 候选
        List<KeywordIndexSearchResult> results = keywordIndexClient.search(
                new KeywordIndexSearchRequest(null, request.question(), request.topK()));

        // 2. 标准化为对话检索片段并保留通道内排名
        return java.util.stream.IntStream.range(0, results.size())
                .mapToObj(index -> toRetrievalChunk(results.get(index), index + 1))
                .toList();
    }

    private RetrievalChunk toRetrievalChunk(KeywordIndexSearchResult result, int rank) {
        return new RetrievalChunk(result.chunkId(), result.documentId(), result.chunkOrder(), result.parentChunkId(),
                null, null, result.text(), result.score(), "BM25", rank);
    }
}
