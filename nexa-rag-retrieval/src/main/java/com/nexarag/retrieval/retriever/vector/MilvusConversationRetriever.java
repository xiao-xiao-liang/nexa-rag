package com.nexarag.retrieval.retriever.vector;

import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.dto.req.ConversationRetrievalRequest;
import com.nexarag.retrieval.index.vector.DocumentVectorStore;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.model.VectorIndexSearchResult;
import com.nexarag.retrieval.retriever.ConversationRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Milvus 的对话向量检索通道。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.retrieval.vector", name = "type", havingValue = "milvus")
public class MilvusConversationRetriever implements ConversationRetriever {

    private final DocumentVectorStore documentVectorStore;
    private final RetrievalProperties retrievalProperties;

    @Override
    public List<RetrievalChunk> retrieve(ConversationRetrievalRequest request) {
        long startTime = System.currentTimeMillis();
        int topK = retrievalProperties.getCandidate().getVectorCandidateLimit();
        double coarseFloor = retrievalProperties.getCandidate().getCoarseScoreFloor();

        log.info("Milvus 对话检索开始，question='{}'，topK：{}，生效版本数：{}",
                request.question(), topK,
                request.activeVersionIds() == null ? 0 : request.activeVersionIds().size());

        // 1. 委托文档向量存储执行模型网关向量化与相似度查询
        List<VectorIndexSearchResult> results = request.activeVersionIds() == null || request.activeVersionIds().isEmpty()
                ? documentVectorStore.search(request.question(), topK)
                : documentVectorStore.search(request.question(), topK, request.activeVersionIds());

        // 2. 标准化通道内排名与粗排门槛过滤
        List<RetrievalChunk> filtered = java.util.stream.IntStream.range(0, results.size())
                .filter(index -> results.get(index).score() >= coarseFloor)
                .mapToObj(index -> toRetrievalChunk(results.get(index), index + 1))
                .toList();

        log.info("Milvus 对话检索完成，原始召回数：{}，过滤阈值：{}，最终候选数：{}，耗时：{}ms",
                results.size(), coarseFloor, filtered.size(), Math.max(0, System.currentTimeMillis() - startTime));

        return filtered;
    }

    private RetrievalChunk toRetrievalChunk(VectorIndexSearchResult result, int rank) {
        return new RetrievalChunk(result.chunkId(), result.documentId(), result.chunkOrder(), result.parentChunkId(),
                null, null, result.text(), result.score(), "MILVUS", rank, result.documentVersionId());
    }
}
