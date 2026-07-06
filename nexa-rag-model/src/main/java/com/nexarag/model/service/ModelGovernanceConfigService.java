package com.nexarag.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.model.dto.ModelGovernanceConfigRequest;
import com.nexarag.model.dto.ModelGovernanceConfigResponse;
import com.nexarag.model.entity.ModelGovernanceConfig;

/**
 * 模型治理配置服务，负责单个模型配置的治理参数查询和保存。
 */
public interface ModelGovernanceConfigService extends IService<ModelGovernanceConfig> {

    /**
     * 根据模型配置ID查询治理配置。
     *
     * @param configId 模型配置ID
     * @return 治理配置
     */
    ModelGovernanceConfig getByConfigId(Long configId);

    /**
     * 根据模型配置ID保存治理配置。
     *
     * @param configId 模型配置ID
     * @param request  治理配置保存请求
     * @return 治理配置
     */
    ModelGovernanceConfig saveByConfigId(Long configId, ModelGovernanceConfigRequest request);

    /**
     * 转换为治理配置响应。
     *
     * @param config 治理配置
     * @return 治理配置响应
     */
    ModelGovernanceConfigResponse toResponse(ModelGovernanceConfig config);
}
