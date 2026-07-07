package com.nexarag.model.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
import com.nexarag.model.config.ModelGovernanceProperties;
import com.nexarag.model.dto.ModelConfigCreateRequest;
import com.nexarag.model.dto.ModelConfigResponse;
import com.nexarag.model.dto.ModelConfigUpdateRequest;
import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.entity.ModelRegistryVersion;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.governance.DefaultModelGovernancePolicyFactory;
import com.nexarag.model.mapper.ModelConfigMapper;
import com.nexarag.model.mapper.ModelRegistryVersionMapper;
import com.nexarag.model.refresh.ModelRegistryChangePublisher;
import com.nexarag.model.security.ModelSecretEncryptor;
import com.nexarag.model.service.ModelConfigService;
import com.nexarag.model.service.ModelGovernanceConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型配置服务实现类，负责模型配置保存、更新和 API Key 加密脱敏。
 */
@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl extends ServiceImpl<ModelConfigMapper, ModelConfig>
        implements ModelConfigService {

    private static final int DEFAULT_TIMEOUT_MS = 30000;
    private static final int DEFAULT_MAX_RETRIES = 0;
    private static final String DEFAULT_CHAT_ENDPOINT_PATH = "/chat/completions";
    private static final String DEFAULT_EMBEDDING_ENDPOINT_PATH = "/embeddings";
    private static final String DEFAULT_RERANK_ENDPOINT_PATH = "/services/rerank/text-rerank/text-rerank";
    private static final String DEFAULT_QWEN3_RERANK_ENDPOINT_PATH = "/compatible-api/v1/reranks";
    private static final String QWEN3_RERANK_MODEL = "qwen3-rerank";
    private static final long INITIAL_CONFIG_VERSION = 1L;
    private static final long DEFAULT_REGISTRY_VERSION_ID = 1L;

    private final ModelSecretEncryptor secretEncryptor;
    private final ModelRegistryVersionMapper modelRegistryVersionMapper;
    private final ModelRegistryChangePublisher modelRegistryChangePublisher;
    private final DefaultModelGovernancePolicyFactory defaultModelGovernancePolicyFactory;
    private final ModelGovernanceConfigService modelGovernanceConfigService;
    private final ModelGovernanceProperties modelGovernanceProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelConfig createConfig(ModelConfigCreateRequest request) {
        validateCreateRequest(request);
        if (existsByConfigKey(request.configKey(), null)) {
            throw new ClientException("模型配置标识已存在，configKey=" + request.configKey(), BaseErrorCode.PARAM_ERROR);
        }

        // 1. 加密并脱敏 API Key
        String apiKeyCipher = secretEncryptor.encrypt(request.apiKey());
        String apiKeyMask = secretEncryptor.mask(request.apiKey());

        // 2. 构建模型配置实体
        ModelConfig config = ModelConfig.builder()
                .configId(IdWorker.getId())
                .configKey(request.configKey())
                .modelType(request.modelType())
                .provider(request.provider())
                .baseUrl(request.baseUrl())
                .endpointPath(normalizeEndpointPath(request.modelType(), request.modelName(), request.endpointPath()))
                .apiKeyCipher(apiKeyCipher)
                .apiKeyMask(apiKeyMask)
                .modelName(request.modelName())
                .enabled(Boolean.TRUE)
                .timeoutMs(normalizeTimeoutMs(request.timeoutMs()))
                .maxRetries(normalizeMaxRetries(request.maxRetries()))
                .version(INITIAL_CONFIG_VERSION)
                .extraConfig(request.extraConfig())
                .remark(request.remark())
                .build();

        // 3. 保存模型配置
        saveConfig(config);

        // 4. 自动创建模型级默认治理配置，已存在时不覆盖
        autoCreateDefaultGovernance(config);

        // 5. 触发模型注册表刷新
        bumpRegistryVersionAndPublish();
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelConfig updateConfig(Long configId, ModelConfigUpdateRequest request) {
        if (configId == null) {
            throw new ClientException("模型配置ID不能为空", BaseErrorCode.PARAM_ERROR);
        }
        ModelConfig config = getRequiredConfig(configId);
        if (StringUtils.hasText(request.configKey()) && existsByConfigKey(request.configKey(), configId)) {
            throw new ClientException("模型配置标识已存在，configKey=" + request.configKey(), BaseErrorCode.PARAM_ERROR);
        }

        // 1. 更新基础配置字段
        applyBasicUpdate(config, request);
        applyEndpointPath(config, request);

        // 2. 仅在传入新 API Key 时更新密钥字段
        if (StringUtils.hasText(request.apiKey())) {
            config.setApiKeyCipher(secretEncryptor.encrypt(request.apiKey()));
            config.setApiKeyMask(secretEncryptor.mask(request.apiKey()));
        }

        // 3. 递增单条配置版本并保存
        config.setVersion(config.getVersion() == null ? INITIAL_CONFIG_VERSION : config.getVersion() + 1);
        updateConfigById(config);

        // 4. 触发模型注册表刷新
        bumpRegistryVersionAndPublish();
        return config;
    }

    @Override
    public ModelConfigResponse getConfigResponse(Long configId) {
        return toResponse(getRequiredConfig(configId));
    }

    @Override
    public List<ModelConfigResponse> listConfigResponses() {
        // 1. 查询未逻辑删除的模型配置并转换为脱敏响应
        return this.lambdaQuery()
                .orderByDesc(ModelConfig::getUpdateTime)
                .list()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Long configId) {
        if (configId == null) {
            throw new ClientException("模型配置ID不能为空", BaseErrorCode.PARAM_ERROR);
        }

        // 1. 确认模型配置存在
        getRequiredConfig(configId);

        // 2. 执行逻辑删除并记录删除时间
        removeConfigById(configId);

        // 3. 触发模型注册表刷新
        bumpRegistryVersionAndPublish();
    }

    /**
     * 判断模型配置标识是否已存在。
     *
     * @param configKey        模型配置标识
     * @param excludedConfigId 需要排除的模型配置ID
     * @return true 表示已存在
     */
    protected boolean existsByConfigKey(String configKey, Long excludedConfigId) {
        return this.lambdaQuery()
                .eq(ModelConfig::getConfigKey, configKey)
                .ne(excludedConfigId != null, ModelConfig::getConfigId, excludedConfigId)
                .exists();
    }

    /**
     * 保存模型配置。
     *
     * @param config 模型配置
     * @return true 表示保存成功
     */
    protected boolean saveConfig(ModelConfig config) {
        return this.save(config);
    }

    /**
     * 根据ID查询模型配置，不存在时抛出异常。
     *
     * @param configId 模型配置ID
     * @return 模型配置
     */
    protected ModelConfig getRequiredConfig(Long configId) {
        ModelConfig config = this.getById(configId);
        if (config == null) {
            throw new ClientException("模型配置不存在，configId=" + configId, BaseErrorCode.PARAM_ERROR);
        }
        return config;
    }

    /**
     * 按ID更新模型配置。
     *
     * @param config 模型配置
     * @return true 表示更新成功
     */
    protected boolean updateConfigById(ModelConfig config) {
        return this.lambdaUpdate()
                .eq(ModelConfig::getConfigId, config.getConfigId())
                .set(ModelConfig::getConfigKey, config.getConfigKey())
                .set(ModelConfig::getModelType, config.getModelType())
                .set(ModelConfig::getProvider, config.getProvider())
                .set(ModelConfig::getBaseUrl, config.getBaseUrl())
                .set(ModelConfig::getEndpointPath, config.getEndpointPath())
                .set(ModelConfig::getApiKeyCipher, config.getApiKeyCipher())
                .set(ModelConfig::getApiKeyMask, config.getApiKeyMask())
                .set(ModelConfig::getModelName, config.getModelName())
                .set(ModelConfig::getEnabled, config.getEnabled())
                .set(ModelConfig::getTimeoutMs, config.getTimeoutMs())
                .set(ModelConfig::getMaxRetries, config.getMaxRetries())
                .set(ModelConfig::getVersion, config.getVersion())
                .set(ModelConfig::getExtraConfig, config.getExtraConfig())
                .set(ModelConfig::getRemark, config.getRemark())
                .update();
    }

    /**
     * 按ID逻辑删除模型配置。
     *
     * @param configId 模型配置ID
     * @return true 表示删除成功
     */
    protected boolean removeConfigById(Long configId) {
        return this.lambdaUpdate()
                .eq(ModelConfig::getConfigId, configId)
                .set(ModelConfig::getDelFlag, 1)
                .set(ModelConfig::getDeleteTime, LocalDateTime.now())
                .update();
    }

    /**
     * 自动创建模型配置对应的默认治理配置。
     *
     * @param config 模型配置
     */
    protected void autoCreateDefaultGovernance(ModelConfig config) {
        if (modelGovernanceProperties == null
                || !Boolean.TRUE.equals(modelGovernanceProperties.getGovernance().getAutoCreateDefault())) {
            return;
        }

        // 1. 按模型类型生成默认治理配置
        ModelGovernanceConfig governanceConfig =
                defaultModelGovernancePolicyFactory.createForConfig(config.getConfigId(), config.getModelType());

        // 2. 保存默认治理配置，已存在时不覆盖
        modelGovernanceConfigService.saveDefaultIfAbsent(governanceConfig);
    }

    /**
     * 递增模型注册表版本并发布刷新消息。
     *
     * @return 最新模型注册表版本号
     */
    protected long bumpRegistryVersionAndPublish() {
        ModelRegistryVersion version = modelRegistryVersionMapper.selectById(DEFAULT_REGISTRY_VERSION_ID);
        long nextVersionNo = version == null ? INITIAL_CONFIG_VERSION : version.getVersionNo() + 1;

        // 1. 写入最新模型注册表版本
        ModelRegistryVersion nextVersion = new ModelRegistryVersion();
        nextVersion.setVersionId(DEFAULT_REGISTRY_VERSION_ID);
        nextVersion.setVersionNo(nextVersionNo);
        if (version == null) {
            modelRegistryVersionMapper.insert(nextVersion);
        } else {
            modelRegistryVersionMapper.updateById(nextVersion);
        }

        // 2. 发布模型注册表刷新消息
        modelRegistryChangePublisher.publish(nextVersionNo);
        return nextVersionNo;
    }

    private void validateCreateRequest(ModelConfigCreateRequest request) {
        if (request == null) {
            throw new ClientException("模型配置创建请求不能为空", BaseErrorCode.PARAM_ERROR);
        }
        if (!StringUtils.hasText(request.configKey())) {
            throw new ClientException("模型配置标识不能为空", BaseErrorCode.PARAM_ERROR);
        }
        if (request.modelType() == null) {
            throw new ClientException("模型类型不能为空", BaseErrorCode.PARAM_ERROR);
        }
        if (request.provider() == null) {
            throw new ClientException("模型厂商不能为空", BaseErrorCode.PARAM_ERROR);
        }
        if (!StringUtils.hasText(request.baseUrl())) {
            throw new ClientException("模型服务地址不能为空", BaseErrorCode.PARAM_ERROR);
        }
        if (!StringUtils.hasText(request.modelName())) {
            throw new ClientException("模型名称不能为空", BaseErrorCode.PARAM_ERROR);
        }
    }

    private void applyBasicUpdate(ModelConfig config, ModelConfigUpdateRequest request) {
        if (StringUtils.hasText(request.configKey())) {
            config.setConfigKey(request.configKey());
        }
        if (request.modelType() != null) {
            config.setModelType(request.modelType());
        }
        if (request.provider() != null) {
            config.setProvider(request.provider());
        }
        if (StringUtils.hasText(request.baseUrl())) {
            config.setBaseUrl(request.baseUrl());
        }
        if (StringUtils.hasText(request.modelName())) {
            config.setModelName(request.modelName());
        }
        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }
        if (request.timeoutMs() != null) {
            config.setTimeoutMs(normalizeTimeoutMs(request.timeoutMs()));
        }
        if (request.maxRetries() != null) {
            config.setMaxRetries(normalizeMaxRetries(request.maxRetries()));
        }
        if (request.extraConfig() != null) {
            config.setExtraConfig(request.extraConfig());
        }
        if (request.remark() != null) {
            config.setRemark(request.remark());
        }
    }

    private ModelConfigResponse toResponse(ModelConfig config) {
        return ModelConfigResponse.builder()
                .configId(config.getConfigId())
                .configKey(config.getConfigKey())
                .modelType(config.getModelType())
                .provider(config.getProvider())
                .baseUrl(config.getBaseUrl())
                .apiKeyMask(config.getApiKeyMask())
                .modelName(config.getModelName())
                .endpointPath(normalizeEndpointPath(config.getModelType(), config.getModelName(), config.getEndpointPath()))
                .enabled(config.getEnabled())
                .timeoutMs(config.getTimeoutMs())
                .maxRetries(config.getMaxRetries())
                .version(config.getVersion())
                .extraConfig(config.getExtraConfig())
                .remark(config.getRemark())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .build();
    }

    private Integer normalizeTimeoutMs(Integer timeoutMs) {
        return timeoutMs == null || timeoutMs <= 0 ? DEFAULT_TIMEOUT_MS : timeoutMs;
    }

    private Integer normalizeMaxRetries(Integer maxRetries) {
        return maxRetries == null || maxRetries < 0 ? DEFAULT_MAX_RETRIES : maxRetries;
    }

    private void applyEndpointPath(ModelConfig config, ModelConfigUpdateRequest request) {
        // 1. 根据最终模型类型和模型名称归一化接口路径
        String endpointPath = StringUtils.hasText(request.endpointPath()) ? request.endpointPath() : config.getEndpointPath();
        config.setEndpointPath(normalizeEndpointPath(config.getModelType(), config.getModelName(), endpointPath));
    }

    private String normalizeEndpointPath(ModelType modelType, String modelName, String endpointPath) {
        if (StringUtils.hasText(endpointPath)) {
            return endpointPath;
        }
        if (ModelType.CHAT == modelType) {
            return DEFAULT_CHAT_ENDPOINT_PATH;
        }
        if (ModelType.EMBEDDING == modelType) {
            return DEFAULT_EMBEDDING_ENDPOINT_PATH;
        }
        if (ModelType.RERANK == modelType && QWEN3_RERANK_MODEL.equals(modelName)) {
            return DEFAULT_QWEN3_RERANK_ENDPOINT_PATH;
        }
        if (ModelType.RERANK == modelType) {
            return DEFAULT_RERANK_ENDPOINT_PATH;
        }
        return null;
    }
}
