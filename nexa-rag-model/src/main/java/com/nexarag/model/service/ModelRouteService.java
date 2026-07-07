package com.nexarag.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.model.dto.ModelRouteCreateRequest;
import com.nexarag.model.dto.ModelRouteResponse;
import com.nexarag.model.dto.ModelRouteUpdateRequest;
import com.nexarag.model.entity.ModelRoute;

import java.util.List;

/**
 * 模型路由服务，负责模型路由数据操作。
 */
public interface ModelRouteService extends IService<ModelRoute> {

    /**
     * 创建模型路由。
     *
     * @param request 模型路由创建请求
     * @return 模型路由
     */
    ModelRoute createRoute(ModelRouteCreateRequest request);

    /**
     * 查询模型路由响应列表。
     *
     * @return 模型路由响应列表
     */
    List<ModelRouteResponse> listRouteResponses();

    /**
     * 查询模型路由响应详情。
     *
     * @param routeId 模型路由ID
     * @return 模型路由响应详情
     */
    ModelRouteResponse getRouteResponse(Long routeId);

    /**
     * 更新模型路由。
     *
     * @param routeId 模型路由ID
     * @param request 更新请求
     * @return 更新后的模型路由
     */
    ModelRoute updateRoute(Long routeId, ModelRouteUpdateRequest request);

    /**
     * 删除模型路由。
     *
     * @param routeId 模型路由ID
     */
    void deleteRoute(Long routeId);

    /**
     * 转换为模型路由响应。
     *
     * @param route 模型路由
     * @return 模型路由响应
     */
    ModelRouteResponse toResponse(ModelRoute route);
}
