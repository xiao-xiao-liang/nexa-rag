package com.nexarag.model.service.impl;

import com.nexarag.model.dto.ModelProviderCatalogResponse;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.service.ModelProviderCatalogService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 模型厂商推荐值服务实现类，提供初版内置厂商推荐配置。
 */
@Service
public class ModelProviderCatalogServiceImpl implements ModelProviderCatalogService {

    private static final String CHAT_ENDPOINT_PATH = "/chat/completions";
    private static final String RERANK_ENDPOINT_PATH = "/compatible-api/v1/reranks";
    private static final String DEFAULT_GOVERNANCE_DESCRIPTION =
            "默认治理会按模型类型自动创建限流、熔断、并发隔离和超时保护配置，可在治理配置中调整。";

    @Override
    public List<ModelProviderCatalogResponse> listProviders() {
        return List.of(
                openai(),
                ollama(),
                dashScope(),
                deepSeek(),
                siliconFlow(),
                zhipu(),
                moonshot(),
                customOpenAi()
        );
    }

    private ModelProviderCatalogResponse openai() {
        return ModelProviderCatalogResponse.builder()
                .provider(ModelProvider.OPENAI)
                .displayName("OpenAI")
                .supportedTypes(List.of(ModelType.CHAT, ModelType.EMBEDDING))
                .defaultBaseUrl("https://api.openai.com/v1")
                .recommendedModels(Map.of(
                        ModelType.CHAT, List.of("gpt-4.1-mini", "gpt-4.1"),
                        ModelType.EMBEDDING, List.of("text-embedding-3-small", "text-embedding-3-large")
                ))
                .apiKeyRequired(true)
                .openAiCompatible(ModelProvider.OPENAI.isOpenAiCompatible())
                .defaultEndpointPath(CHAT_ENDPOINT_PATH)
                .defaultGovernanceDescription(DEFAULT_GOVERNANCE_DESCRIPTION)
                .build();
    }

    private ModelProviderCatalogResponse ollama() {
        return ModelProviderCatalogResponse.builder()
                .provider(ModelProvider.OLLAMA)
                .displayName("Ollama")
                .supportedTypes(List.of(ModelType.CHAT, ModelType.EMBEDDING))
                .defaultBaseUrl("http://localhost:11434/v1")
                .recommendedModels(Map.of(
                        ModelType.CHAT, List.of("qwen2.5:7b"),
                        ModelType.EMBEDDING, List.of("nomic-embed-text")
                ))
                .apiKeyRequired(false)
                .openAiCompatible(ModelProvider.OLLAMA.isOpenAiCompatible())
                .defaultEndpointPath(CHAT_ENDPOINT_PATH)
                .defaultGovernanceDescription(DEFAULT_GOVERNANCE_DESCRIPTION)
                .build();
    }

    private ModelProviderCatalogResponse dashScope() {
        return ModelProviderCatalogResponse.builder()
                .provider(ModelProvider.DASHSCOPE)
                .displayName("DashScope")
                .supportedTypes(List.of(ModelType.CHAT, ModelType.EMBEDDING, ModelType.RERANK))
                .defaultBaseUrl("https://dashscope.aliyuncs.com")
                .recommendedModels(Map.of(
                        ModelType.CHAT, List.of("qwen-plus", "qwen-max"),
                        ModelType.EMBEDDING, List.of("text-embedding-v4"),
                        ModelType.RERANK, List.of("qwen3-rerank")
                ))
                .apiKeyRequired(true)
                .openAiCompatible(ModelProvider.DASHSCOPE.isOpenAiCompatible())
                .defaultEndpointPath(RERANK_ENDPOINT_PATH)
                .defaultGovernanceDescription(DEFAULT_GOVERNANCE_DESCRIPTION)
                .build();
    }

    private ModelProviderCatalogResponse deepSeek() {
        return openAiCompatible(ModelProvider.DEEPSEEK, "DeepSeek", "https://api.deepseek.com/v1");
    }

    private ModelProviderCatalogResponse siliconFlow() {
        return openAiCompatible(ModelProvider.SILICONFLOW, "SiliconFlow", "https://api.siliconflow.cn/v1");
    }

    private ModelProviderCatalogResponse zhipu() {
        return openAiCompatible(ModelProvider.ZHIPU, "智谱 AI", "https://open.bigmodel.cn/api/paas/v4");
    }

    private ModelProviderCatalogResponse moonshot() {
        return openAiCompatible(ModelProvider.MOONSHOT, "Moonshot", "https://api.moonshot.cn/v1");
    }

    private ModelProviderCatalogResponse customOpenAi() {
        return openAiCompatible(ModelProvider.CUSTOM_OPENAI, "自定义 OpenAI 兼容服务", "");
    }

    private ModelProviderCatalogResponse openAiCompatible(ModelProvider provider, String displayName, String baseUrl) {
        return ModelProviderCatalogResponse.builder()
                .provider(provider)
                .displayName(displayName)
                .supportedTypes(List.of(ModelType.CHAT, ModelType.EMBEDDING))
                .defaultBaseUrl(baseUrl)
                .recommendedModels(Map.of())
                .apiKeyRequired(true)
                .openAiCompatible(provider.isOpenAiCompatible())
                .defaultEndpointPath(CHAT_ENDPOINT_PATH)
                .defaultGovernanceDescription(DEFAULT_GOVERNANCE_DESCRIPTION)
                .build();
    }
}
