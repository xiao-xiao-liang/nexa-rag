package com.nexarag.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.model.dto.ModelRouteCreateRequest;
import com.nexarag.model.entity.ModelRoute;

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
}
