package com.nexarag.model.provider.dashscope;

import com.alibaba.cloud.ai.document.DocumentWithScore;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import com.alibaba.cloud.ai.model.RerankResponseMetadata;
import com.nexarag.model.client.ModelClientFactory;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.rerank.RerankCandidate;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.model.provider.ModelProviderAdapter;
import com.nexarag.model.route.ModelRouteDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DashScope Rerank Provider，负责调用阿里云 DashScope 重排序模型。
 */
@Component
@RequiredArgsConstructor
public class DashScopeRerankProvider implements ModelProviderAdapter {

    private static final String CANDIDATE_ID_KEY = "nexa_candidate_id";

    private final ModelClientFactory modelClientFactory;

    @Override
    public boolean supports(ModelProvider provider, ModelType modelType) {
        return ModelProvider.DASHSCOPE == provider && ModelType.RERANK == modelType;
    }

    @Override
    public RerankModelResponse rerank(ModelRouteDecision decision, RerankModelRequest request) {
        // 1. 将业务候选内容转换为 Spring AI Alibaba Rerank 请求
        RerankRequest rerankRequest = new RerankRequest(request.query(), toDocuments(request.candidates()));
        RerankResponse response = modelClientFactory.getDashScopeRerankClient(decision).call(rerankRequest);

        // 2. 将框架返回结果转换为模型网关统一响应
        List<RerankModelResponse.RerankScore> scores = response.getResults().stream()
                .map(this::toScore)
                .toList();
        return new RerankModelResponse(scores, decision.profileName(), totalTokens(response.getMetadata()));
    }

    private List<Document> toDocuments(List<RerankCandidate> candidates) {
        return candidates.stream()
                .map(this::toDocument)
                .toList();
    }

    private Document toDocument(RerankCandidate candidate) {
        Map<String, Object> metadata = new HashMap<>(candidate.metadata());
        metadata.put(CANDIDATE_ID_KEY, candidate.id());
        return new Document(candidate.text(), metadata);
    }

    private RerankModelResponse.RerankScore toScore(DocumentWithScore documentWithScore) {
        Document document = documentWithScore.getOutput();
        Object candidateId = document.getMetadata().get(CANDIDATE_ID_KEY);
        double score = documentWithScore.getScore() == null ? 0D : documentWithScore.getScore();
        return new RerankModelResponse.RerankScore(String.valueOf(candidateId), score);
    }

    private Integer totalTokens(RerankResponseMetadata metadata) {
        if (metadata == null || metadata.getUsage() == null || metadata.getUsage().getTotalTokens() == null) {
            return 0;
        }
        return metadata.getUsage().getTotalTokens();
    }
}
