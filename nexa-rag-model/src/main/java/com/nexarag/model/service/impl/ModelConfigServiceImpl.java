package com.nexarag.model.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
import com.nexarag.model.dto.ModelConfigCreateRequest;
import com.nexarag.model.dto.ModelConfigResponse;
import com.nexarag.model.dto.ModelConfigUpdateRequest;
import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.mapper.ModelConfigMapper;
import com.nexarag.model.security.ModelSecretEncryptor;
import com.nexarag.model.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 模型配置服务实现类，负责模型配置保存、更新和 API Key 加密脱敏。
 */
@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl extends ServiceImpl<ModelConfigMapper, ModelConfig>
        implements ModelConfigService {

    private static final int DEFAULT_TIMEOUT_MS = 30000;
    private static final int DEFAULT_MAX_RETRIES = 0;
    private static final long INITIAL_CONFIG_VERSION = 1L;

    private final ModelSecretEncryptor secretEncryptor;

    @Override
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
        return config;
    }

    @Override
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

        // 2. 仅在传入新 API Key 时更新密钥字段
        if (StringUtils.hasText(request.apiKey())) {
            config.setApiKeyCipher(secretEncryptor.encrypt(request.apiKey()));
            config.setApiKeyMask(secretEncryptor.mask(request.apiKey()));
        }

        // 3. 递增单条配置版本并保存
        config.setVersion(config.getVersion() == null ? INITIAL_CONFIG_VERSION : config.getVersion() + 1);
        updateConfigById(config);
        return config;
    }

    @Override
    public ModelConfigResponse getConfigResponse(Long configId) {
        return toResponse(getRequiredConfig(configId));
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
        return this.updateById(config);
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
}
