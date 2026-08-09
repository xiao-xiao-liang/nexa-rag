package com.nexarag.retrieval.retriever.vector;

import com.nexarag.retrieval.dto.req.ConversationRetrievalRequest;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.index.vector.DocumentVectorStore;
import com.nexarag.retrieval.model.VectorIndexSearchResult;
import com.nexarag.retrieval.retriever.ConversationRetriever;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Milvus 的对话向量检索通道。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.retrieval.vector", name = "type", havingValue = "milvus")
public class MilvusConversationRetriever implements ConversationRetriever {

    private final DocumentVectorStore documentVectorStore;
    private final RetrievalProperties retrievalProperties;

    @Override
    public List<RetrievalChunk> retrieve(ConversationRetrievalRequest request) {
        // 1. 委托文档向量存储执行模型网关向量化与相似度查询
        List<VectorIndexSearchResult> results = documentVectorStore.search(request.question(),
                retrievalProperties.getCandidate().getVectorCandidateLimit());

        // 2. 标准化通道内排名
        return java.util.stream.IntStream.range(0, results.size())
                .filter(index -> results.get(index).score() >= retrievalProperties.getCandidate().getCoarseScoreFloor())
                .mapToObj(index -> toRetrievalChunk(results.get(index), index + 1))
                .toList();
    }

    private RetrievalChunk toRetrievalChunk(VectorIndexSearchResult result, int rank) {
        return new RetrievalChunk(result.chunkId(), result.documentId(), result.chunkOrder(), result.parentChunkId(),
                null, null, result.text(), result.score(), "MILVUS", rank);
    }
}
