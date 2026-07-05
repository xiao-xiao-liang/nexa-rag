package com.nexarag.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.model.dto.ModelConfigCreateRequest;
import com.nexarag.model.dto.ModelConfigResponse;
import com.nexarag.model.dto.ModelConfigUpdateRequest;
import com.nexarag.model.entity.ModelConfig;

/**
 * 模型配置服务，负责模型配置的创建、更新和脱敏响应转换。
 */
public interface ModelConfigService extends IService<ModelConfig> {

    /**
     * 创建模型配置。
     *
     * @param request 创建请求
     * @return 模型配置实体
     */
    ModelConfig createConfig(ModelConfigCreateRequest request);

    /**
     * 更新模型配置。
     *
     * @param configId 模型配置ID
     * @param request  更新请求
     * @return 模型配置实体
     */
    ModelConfig updateConfig(Long configId, ModelConfigUpdateRequest request);

    /**
     * 查询模型配置响应。
     *
     * @param configId 模型配置ID
     * @return 模型配置脱敏响应
     */
    ModelConfigResponse getConfigResponse(Long configId);
}
