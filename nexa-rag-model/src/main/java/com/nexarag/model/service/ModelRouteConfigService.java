package com.nexarag.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.model.dto.ModelRouteConfigCreateRequest;
import com.nexarag.model.dto.ModelRouteConfigResponse;
import com.nexarag.model.dto.ModelRouteConfigUpdateRequest;
import com.nexarag.model.entity.ModelRouteConfig;

import java.util.List;

/**
 * 模型路由配置关联服务，负责路由与模型配置关系数据操作。
 */
public interface ModelRouteConfigService extends IService<ModelRouteConfig> {

    /**
     * 创建模型路由候选配置。
     *
     * @param routeId 模型路由ID
     * @param request 创建请求
     * @return 模型路由候选配置
     */
    ModelRouteConfig createRouteConfig(Long routeId, ModelRouteConfigCreateRequest request);

    /**
     * 查询模型路由候选配置响应列表。
     *
     * @param routeId 模型路由ID
     * @return 模型路由候选配置响应列表
     */
    List<ModelRouteConfigResponse> listRouteConfigResponses(Long routeId);

    /**
     * 更新模型路由候选配置。
     *
     * @param routeId        模型路由ID
     * @param routeConfigId  模型路由候选配置ID
     * @param request        更新请求
     * @return 更新后的模型路由候选配置
     */
    ModelRouteConfig updateRouteConfig(Long routeId, Long routeConfigId, ModelRouteConfigUpdateRequest request);

    /**
     * 删除模型路由候选配置。
     *
     * @param routeId       模型路由ID
     * @param routeConfigId 模型路由候选配置ID
     */
    void deleteRouteConfig(Long routeId, Long routeConfigId);

    /**
     * 判断模型配置是否仍被路由候选引用。
     *
     * @param configId 模型配置ID
     * @return true 表示存在引用
     */
    boolean existsByConfigId(Long configId);

    /**
     * 判断模型路由下是否仍存在候选配置。
     *
     * @param routeId 模型路由ID
     * @return true 表示存在候选配置
     */
    boolean existsByRouteId(Long routeId);

    /**
     * 转换为模型路由候选配置响应。
     *
     * @param routeConfig 模型路由候选配置
     * @return 模型路由候选配置响应
     */
    ModelRouteConfigResponse toResponse(ModelRouteConfig routeConfig);
}
