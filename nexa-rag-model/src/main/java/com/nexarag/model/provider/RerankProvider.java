package com.nexarag.model.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexarag.model.client.RerankClientFactory;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.rerank.RerankCandidate;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.model.route.ModelRouteDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Rerank Provider，负责基于 RestClient 按模型配置调用重排序模型。
 */
@Component
@RequiredArgsConstructor
public class RerankProvider implements ModelProviderAdapter {

    private static final String QWEN3_RERANK_MODEL = "qwen3-rerank";
    private static final String DEFAULT_QWEN3_RERANK_ENDPOINT_PATH = "/compatible-api/v1/reranks";
    private static final String DEFAULT_SERVICE_RERANK_ENDPOINT_PATH = "/services/rerank/text-rerank/text-rerank";

    private final RerankClientFactory rerankClientFactory;

    @Override
    public boolean supports(ModelProvider provider, ModelType modelType) {
        return ModelType.RERANK == modelType;
    }

    @Override
    public RerankModelResponse rerank(ModelRouteDecision decision, RerankModelRequest request) {
        // 1. 根据模型名称选择请求协议，qwen3-rerank 使用 DashScope 兼容 reranks 协议
        if (isQwen3Rerank(decision.profile().getModelName())) {
            return rerankQwen3(decision, request);
        }

        // 2. 其他 DashScope Rerank 模型使用 services/rerank 协议
        return rerankService(decision, request);
    }

    private RerankModelResponse rerankQwen3(ModelRouteDecision decision, RerankModelRequest request) {
        Qwen3RerankResponse response = post(decision, endpointPath(decision.profile()), new Qwen3RerankRequest(
                decision.profile().getModelName(),
                toDocumentTexts(request.candidates()),
                request.query(),
                topN(request.candidates())));
        List<RerankModelResponse.RerankScore> scores = response == null || CollectionUtils.isEmpty(response.results())
                ? List.of()
                : response.results().stream()
                .map(result -> toScore(result, request.candidates()))
                .toList();
        return new RerankModelResponse(scores, decision.profileName(), totalTokens(response));
    }

    private RerankModelResponse rerankService(ModelRouteDecision decision, RerankModelRequest request) {
        ServiceRerankResponse response = post(decision, endpointPath(decision.profile()), new ServiceRerankRequest(
                decision.profile().getModelName(),
                new ServiceRerankInput(request.query(), toDocumentTexts(request.candidates())),
                new ServiceRerankParameters(true, topN(request.candidates()))));
        List<ServiceRerankResult> results = response == null || response.output() == null ? List.of()
                : response.output().results();
        List<RerankModelResponse.RerankScore> scores = CollectionUtils.isEmpty(results) ? List.of()
                : results.stream()
                .map(result -> toScore(result, request.candidates()))
                .toList();
        return new RerankModelResponse(scores, decision.profileName(), totalTokens(response));
    }

    private <T> T post(ModelRouteDecision decision, String endpointPath, Object body) {
        // 1. 使用按 endpoint 缓存的 RestClient 发起 JSON 请求
        RestClient restClient = rerankClientFactory.getRerankClient(decision);
        @SuppressWarnings("unchecked")
        Class<T> responseType = (Class<T>) (isQwen3Rerank(decision.profile().getModelName())
                ? Qwen3RerankResponse.class : ServiceRerankResponse.class);
        return restClient.post()
                .uri(endpointPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(responseType);
    }

    private List<String> toDocumentTexts(List<RerankCandidate> candidates) {
        return candidates.stream()
                .map(RerankCandidate::text)
                .toList();
    }

    private RerankModelResponse.RerankScore toScore(Qwen3RerankResult result, List<RerankCandidate> candidates) {
        RerankCandidate candidate = candidates.get(result.index());
        double score = result.relevanceScore() == null ? 0D : result.relevanceScore();
        return new RerankModelResponse.RerankScore(candidate.id(), score);
    }

    private RerankModelResponse.RerankScore toScore(ServiceRerankResult result, List<RerankCandidate> candidates) {
        RerankCandidate candidate = candidates.get(result.index());
        double score = result.relevanceScore() == null ? 0D : result.relevanceScore();
        return new RerankModelResponse.RerankScore(candidate.id(), score);
    }

    private String endpointPath(ModelProfileProperties profile) {
        if (StringUtils.hasText(profile.getEndpointPath())) {
            return profile.getEndpointPath();
        }
        return isQwen3Rerank(profile.getModelName())
                ? DEFAULT_QWEN3_RERANK_ENDPOINT_PATH : DEFAULT_SERVICE_RERANK_ENDPOINT_PATH;
    }

    private int topN(List<RerankCandidate> candidates) {
        return candidates == null ? 0 : candidates.size();
    }

    private Integer totalTokens(Qwen3RerankResponse response) {
        if (response == null || response.usage() == null || response.usage().totalTokens() == null) {
            return null;
        }
        return response.usage().totalTokens();
    }

    private Integer totalTokens(ServiceRerankResponse response) {
        if (response == null || response.usage() == null || response.usage().totalTokens() == null) {
            return null;
        }
        return response.usage().totalTokens();
    }

    private boolean isQwen3Rerank(String modelName) {
        return QWEN3_RERANK_MODEL.equals(modelName);
    }

    private record Qwen3RerankRequest(String model, List<String> documents, String query,
                                      @JsonProperty("top_n") Integer topN) {
    }

    private record Qwen3RerankResponse(List<Qwen3RerankResult> results, RerankUsage usage) {
    }

    private record Qwen3RerankResult(Integer index, @JsonProperty("relevance_score") Double relevanceScore) {
    }

    private record ServiceRerankRequest(String model, ServiceRerankInput input, ServiceRerankParameters parameters) {
    }

    private record ServiceRerankInput(String query, List<String> documents) {
    }

    private record ServiceRerankParameters(@JsonProperty("return_documents") Boolean returnDocuments,
                                           @JsonProperty("top_n") Integer topN) {
    }

    private record ServiceRerankResponse(ServiceRerankOutput output, RerankUsage usage) {
    }

    private record ServiceRerankOutput(List<ServiceRerankResult> results) {
    }

    private record ServiceRerankResult(Integer index, @JsonProperty("relevance_score") Double relevanceScore,
                                       Map<String, Object> document) {
    }

    private record RerankUsage(@JsonProperty("total_tokens") Integer totalTokens) {
    }
}
