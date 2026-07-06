package com.nexarag.model.service.impl;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.AbstractException;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.dto.ModelConnectionTestRequest;
import com.nexarag.model.dto.ModelConnectionTestResponse;
import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelResponse;
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
    private static final String DEFAULT_CHAT_PROMPT = "你好";
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
                case CHAT -> testConfigChat(config, start);
                case EMBEDDING -> testConfigEmbedding(config, start);
                case RERANK -> testConfigRerank(config, start);
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
                case CHAT -> testRouteChat(route, start);
                case EMBEDDING -> testRouteEmbedding(route, start);
                case RERANK -> testRouteRerank(route, start);
            };
        } catch (Exception exception) {
            // 2. 将异常转换为连接测试失败响应，避免测试接口直接抛出 500
            return failure(null, route.getModelType(), null, null, start, exception);
        }
    }

    private ModelConnectionTestResponse testConfigChat(ModelConfig config, long start) {
        ChatModelResponse response = modelGateway.chat(toDecision(config), chatRequest("config:" + config.getConfigId()));
        validateChatResponse(response);
        return success(config.getProvider(), config.getModelType(), config.getModelName(), config.getBaseUrl(),
                start, null, null);
    }

    private ModelConnectionTestResponse testConfigEmbedding(ModelConfig config, long start) {
        EmbeddingModelResponse response = modelGateway.embedding(toDecision(config),
                embeddingRequest("config:" + config.getConfigId()));
        Integer vectorDimension = vectorDimension(response);
        return success(config.getProvider(), config.getModelType(), config.getModelName(), config.getBaseUrl(),
                start, vectorDimension, null);
    }

    private ModelConnectionTestResponse testConfigRerank(ModelConfig config, long start) {
        RerankModelResponse response = modelGateway.rerank(toDecision(config),
                rerankRequest("config:" + config.getConfigId()));
        Integer rerankCount = rerankCount(response);
        return success(config.getProvider(), config.getModelType(), config.getModelName(), config.getBaseUrl(),
                start, null, rerankCount);
    }

    private ModelConnectionTestResponse testRouteChat(ModelRoute route, long start) {
        ChatModelResponse response = modelGateway.chat(chatRequest(route.getRouteKey()));
        validateChatResponse(response);
        return success(null, route.getModelType(), null, null, start, null, null);
    }

    private ModelConnectionTestResponse testRouteEmbedding(ModelRoute route, long start) {
        EmbeddingModelResponse response = modelGateway.embedding(embeddingRequest(route.getRouteKey()));
        Integer vectorDimension = vectorDimension(response);
        return success(null, route.getModelType(), null, null, start, vectorDimension, null);
    }

    private ModelConnectionTestResponse testRouteRerank(ModelRoute route, long start) {
        RerankModelResponse response = modelGateway.rerank(rerankRequest(route.getRouteKey()));
        Integer rerankCount = rerankCount(response);
        return success(null, route.getModelType(), null, null, start, null, rerankCount);
    }

    private ChatModelRequest chatRequest(String routeKey) {
        return ChatModelRequest.builder()
                .traceId(UUID.randomUUID().toString())
                .bizType(ModelBizType.MODEL_TEST)
                .bizId(routeKey)
                .routeKey(routeKey)
                .messages(List.of(new ChatModelRequest.ChatMessage("USER", DEFAULT_CHAT_PROMPT)))
                .options(Map.of())
                .build();
    }

    private EmbeddingModelRequest embeddingRequest(String routeKey) {
        return EmbeddingModelRequest.builder()
                .traceId(UUID.randomUUID().toString())
                .bizType(ModelBizType.MODEL_TEST)
                .bizId(routeKey)
                .routeKey(routeKey)
                .texts(List.of(DEFAULT_EMBEDDING_INPUT))
                .build();
    }

    private RerankModelRequest rerankRequest(String routeKey) {
        return RerankModelRequest.builder()
                .traceId(UUID.randomUUID().toString())
                .bizType(ModelBizType.MODEL_TEST)
                .bizId(routeKey)
                .routeKey(routeKey)
                .query(DEFAULT_RERANK_QUERY)
                .candidates(candidates())
                .build();
    }

    private ModelRouteDecision toDecision(ModelConfig config) {
        ModelProfileProperties profile = ModelProfileProperties.builder()
                .provider(config.getProvider().name())
                .baseUrl(config.getBaseUrl())
                .endpointPath(config.getEndpointPath())
                .apiKey(decryptApiKey(config))
                .modelName(config.getModelName())
                .timeoutMs(config.getTimeoutMs() == null ? 60000L : config.getTimeoutMs())
                .build();
        return new ModelRouteDecision(config.getConfigKey(), profile, false);
    }

    private String decryptApiKey(ModelConfig config) {
        if (!StringUtils.hasText(config.getApiKeyCipher())) {
            return "";
        }
        return modelSecretEncryptor.decrypt(config.getApiKeyCipher());
    }

    private List<RerankCandidate> candidates() {
        return IntStream.range(0, DEFAULT_RERANK_DOCUMENTS.size())
                .mapToObj(index -> new RerankCandidate("doc-" + (index + 1),
                        DEFAULT_RERANK_DOCUMENTS.get(index), Map.of()))
                .toList();
    }

    private void validateChatResponse(ChatModelResponse response) {
        if (response == null || !StringUtils.hasText(response.content())) {
            throw new ServiceException("Chat 模型连接测试未返回有效内容", BaseErrorCode.SERVICE_ERROR);
        }
    }

    private Integer vectorDimension(EmbeddingModelResponse response) {
        if (response == null || response.embeddings() == null || response.embeddings().isEmpty()
                || response.embeddings().getFirst() == null || response.embeddings().getFirst().length == 0) {
            throw new ServiceException("Embedding 模型连接测试未返回有效向量", BaseErrorCode.SERVICE_ERROR);
        }
        return response.embeddings().getFirst().length;
    }

    private Integer rerankCount(RerankModelResponse response) {
        if (response == null || response.scores() == null || response.scores().isEmpty()) {
            throw new ServiceException("Rerank 模型连接测试未返回有效分数", BaseErrorCode.SERVICE_ERROR);
        }
        return response.scores().size();
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
