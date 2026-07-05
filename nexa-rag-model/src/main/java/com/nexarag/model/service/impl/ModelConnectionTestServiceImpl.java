package com.nexarag.model.service.impl;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.AbstractException;
import com.nexarag.common.exception.ClientException;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.dto.ModelConnectionTestRequest;
import com.nexarag.model.dto.ModelConnectionTestResponse;
import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.model.gateway.rerank.RerankCandidate;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.model.route.ModelRouteDecision;
import com.nexarag.model.security.ModelSecretEncryptor;
import com.nexarag.model.service.ModelConfigService;
import com.nexarag.model.service.ModelConnectionTestService;
import com.nexarag.model.service.ModelRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * 模型连接测试服务实现类，负责组装测试请求并调用统一模型网关。
 */
@Service
@RequiredArgsConstructor
public class ModelConnectionTestServiceImpl implements ModelConnectionTestService {

    private static final String DEFAULT_EMBEDDING_INPUT = "你好，NexaRAG";
    private static final String DEFAULT_RERANK_QUERY = "什么是 RAG？";
    private static final List<String> DEFAULT_RERANK_DOCUMENTS = List.of(
            "RAG 是检索增强生成。",
            "今天天气很好。"
    );

    private final ModelConfigService modelConfigService;
    private final ModelRouteService modelRouteService;
    private final ModelSecretEncryptor modelSecretEncryptor;
    private final ModelGateway modelGateway;

    @Override
    public ModelConnectionTestResponse testConfig(Long configId, ModelConnectionTestRequest request) {
        long start = System.currentTimeMillis();
        ModelConfig config = getRequiredConfig(configId);
        try {
            // 1. 根据配置类型选择对应测试调用
            return switch (config.getModelType()) {
                case EMBEDDING -> testConfigEmbedding(config, request, start);
                case RERANK -> testConfigRerank(config, request, start);
                case CHAT -> unsupported(config.getProvider(), config.getModelType(), config.getModelName(),
                        config.getBaseUrl(), start);
            };
        } catch (Exception exception) {
            // 2. 将异常转换为连接测试失败响应，避免测试接口直接抛出 500
            return failure(config.getProvider(), config.getModelType(), config.getModelName(), config.getBaseUrl(),
                    start, exception);
        }
    }

    @Override
    public ModelConnectionTestResponse testRoute(Long routeId, ModelConnectionTestRequest request) {
        long start = System.currentTimeMillis();
        ModelRoute route = getRequiredRoute(routeId);
        try {
            // 1. 根据路由类型选择对应测试调用
            return switch (route.getModelType()) {
                case EMBEDDING -> testRouteEmbedding(route, request, start);
                case RERANK -> testRouteRerank(route, request, start);
                case CHAT -> unsupported(null, route.getModelType(), null, null, start);
            };
        } catch (Exception exception) {
            // 2. 将异常转换为连接测试失败响应，避免测试接口直接抛出 500
            return failure(null, route.getModelType(), null, null, start, exception);
        }
    }

    private ModelConnectionTestResponse testConfigEmbedding(ModelConfig config, ModelConnectionTestRequest request,
                                                            long start) {
        EmbeddingModelResponse response = modelGateway.embedding(toDecision(config),
                embeddingRequest("config:" + config.getConfigId(), request));
        Integer vectorDimension = response.embeddings().isEmpty() ? 0 : response.embeddings().getFirst().length;
        return success(config.getProvider(), config.getModelType(), config.getModelName(), config.getBaseUrl(),
                start, vectorDimension, null);
    }

    private ModelConnectionTestResponse testConfigRerank(ModelConfig config, ModelConnectionTestRequest request,
                                                         long start) {
        RerankModelResponse response = modelGateway.rerank(toDecision(config),
                rerankRequest("config:" + config.getConfigId(), request));
        return success(config.getProvider(), config.getModelType(), config.getModelName(), config.getBaseUrl(),
                start, null, response.scores().size());
    }

    private ModelConnectionTestResponse testRouteEmbedding(ModelRoute route, ModelConnectionTestRequest request,
                                                           long start) {
        EmbeddingModelResponse response = modelGateway.embedding(embeddingRequest(route.getRouteKey(), request));
        Integer vectorDimension = response.embeddings().isEmpty() ? 0 : response.embeddings().getFirst().length;
        return success(null, route.getModelType(), null, null, start, vectorDimension, null);
    }

    private ModelConnectionTestResponse testRouteRerank(ModelRoute route, ModelConnectionTestRequest request,
                                                        long start) {
        RerankModelResponse response = modelGateway.rerank(rerankRequest(route.getRouteKey(), request));
        return success(null, route.getModelType(), null, null, start, null, response.scores().size());
    }

    private EmbeddingModelRequest embeddingRequest(String routeKey, ModelConnectionTestRequest request) {
        return EmbeddingModelRequest.builder()
                .traceId(UUID.randomUUID().toString())
                .bizType(ModelBizType.MODEL_TEST)
                .bizId(routeKey)
                .routeKey(routeKey)
                .texts(List.of(input(request)))
                .build();
    }

    private RerankModelRequest rerankRequest(String routeKey, ModelConnectionTestRequest request) {
        return RerankModelRequest.builder()
                .traceId(UUID.randomUUID().toString())
                .bizType(ModelBizType.MODEL_TEST)
                .bizId(routeKey)
                .routeKey(routeKey)
                .query(query(request))
                .candidates(candidates(request))
                .build();
    }

    private ModelRouteDecision toDecision(ModelConfig config) {
        ModelProfileProperties profile = new ModelProfileProperties();
        profile.setProvider(config.getProvider().name());
        profile.setBaseUrl(config.getBaseUrl());
        profile.setApiKey(decryptApiKey(config));
        profile.setModelName(config.getModelName());
        profile.setTimeoutMs(config.getTimeoutMs() == null ? 60000L : config.getTimeoutMs());
        return new ModelRouteDecision(config.getConfigKey(), profile, false);
    }

    private String decryptApiKey(ModelConfig config) {
        if (!StringUtils.hasText(config.getApiKeyCipher())) {
            return "";
        }
        return modelSecretEncryptor.decrypt(config.getApiKeyCipher());
    }

    private List<RerankCandidate> candidates(ModelConnectionTestRequest request) {
        List<String> documents = request == null || CollectionUtils.isEmpty(request.documents())
                ? DEFAULT_RERANK_DOCUMENTS : request.documents();
        return IntStream.range(0, documents.size())
                .mapToObj(index -> new RerankCandidate("doc-" + (index + 1), documents.get(index), Map.of()))
                .toList();
    }

    private String input(ModelConnectionTestRequest request) {
        if (request == null || !StringUtils.hasText(request.input())) {
            return DEFAULT_EMBEDDING_INPUT;
        }
        return request.input();
    }

    private String query(ModelConnectionTestRequest request) {
        if (request == null || !StringUtils.hasText(request.query())) {
            return DEFAULT_RERANK_QUERY;
        }
        return request.query();
    }

    private ModelConfig getRequiredConfig(Long configId) {
        ModelConfig config = modelConfigService.getById(configId);
        if (Objects.isNull(config)) {
            throw new ClientException("模型配置不存在，configId=" + configId, BaseErrorCode.PARAM_ERROR);
        }
        return config;
    }

    private ModelRoute getRequiredRoute(Long routeId) {
        ModelRoute route = modelRouteService.getById(routeId);
        if (Objects.isNull(route)) {
            throw new ClientException("模型路由不存在，routeId=" + routeId, BaseErrorCode.PARAM_ERROR);
        }
        return route;
    }

    private ModelConnectionTestResponse success(ModelProvider provider, ModelType modelType, String modelName,
                                                String baseUrl, long start, Integer vectorDimension,
                                                Integer rerankCount) {
        return ModelConnectionTestResponse.builder()
                .success(true)
                .provider(provider)
                .modelType(modelType)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .durationMs(durationMs(start))
                .vectorDimension(vectorDimension)
                .rerankCount(rerankCount)
                .build();
    }

    private ModelConnectionTestResponse unsupported(ModelProvider provider, ModelType modelType, String modelName,
                                                    String baseUrl, long start) {
        return ModelConnectionTestResponse.builder()
                .success(false)
                .provider(provider)
                .modelType(modelType)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .durationMs(durationMs(start))
                .errorCode(BaseErrorCode.SERVICE_ERROR.code())
                .errorMessage("Chat 模型连接测试暂未支持")
                .build();
    }

    private ModelConnectionTestResponse failure(ModelProvider provider, ModelType modelType, String modelName,
                                                String baseUrl, long start, Exception exception) {
        String errorCode = exception instanceof AbstractException abstractException
                ? abstractException.getErrorCode() : BaseErrorCode.SERVICE_ERROR.code();
        String errorMessage = exception instanceof AbstractException abstractException
                ? abstractException.getErrorMessage() : exception.getMessage();
        return ModelConnectionTestResponse.builder()
                .success(false)
                .provider(provider)
                .modelType(modelType)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .durationMs(durationMs(start))
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    private long durationMs(long start) {
        return Math.max(0, System.currentTimeMillis() - start);
    }
}
