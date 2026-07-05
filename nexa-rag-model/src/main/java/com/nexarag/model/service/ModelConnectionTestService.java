package com.nexarag.model.service;

import com.nexarag.model.dto.ModelConnectionTestRequest;
import com.nexarag.model.dto.ModelConnectionTestResponse;

/**
 * 模型连接测试服务，负责验证模型配置或模型路由是否可调用。
 */
public interface ModelConnectionTestService {

    /**
     * 测试指定模型配置。
     *
     * @param configId 模型配置ID
     * @param request  测试请求
     * @return 测试结果
     */
    ModelConnectionTestResponse testConfig(Long configId, ModelConnectionTestRequest request);

    /**
     * 测试指定模型路由。
     *
     * @param routeId 模型路由ID
     * @param request 测试请求
     * @return 测试结果
     */
    ModelConnectionTestResponse testRoute(Long routeId, ModelConnectionTestRequest request);
}
