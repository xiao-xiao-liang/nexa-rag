package com.nexarag.model.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.model.dto.ModelRouteConfigCreateRequest;
import com.nexarag.model.dto.ModelRouteConfigResponse;
import com.nexarag.model.dto.ModelRouteConfigUpdateRequest;
import com.nexarag.model.service.ModelRouteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型路由候选配置 Controller，负责路由下模型配置候选关系的 REST 接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model/routes/{routeId}/configs")
public class ModelRouteConfigController {

    private final ModelRouteConfigService modelRouteConfigService;

    /**
     * 查询模型路由候选配置列表。
     *
     * @param routeId 模型路由ID
     * @return 模型路由候选配置列表
     */
    @GetMapping
    public Result<List<ModelRouteConfigResponse>> listRouteConfigs(@PathVariable Long routeId) {
        // 1. 查询路由下的候选配置列表
        return Results.success(modelRouteConfigService.listRouteConfigResponses(routeId));
    }

    /**
     * 创建模型路由候选配置。
     *
     * @param routeId 模型路由ID
     * @param request 创建请求
     * @return 创建后的模型路由候选配置
     */
    @PostMapping
    public Result<ModelRouteConfigResponse> createRouteConfig(@PathVariable Long routeId,
                                                              @RequestBody ModelRouteConfigCreateRequest request) {
        // 1. 创建路由候选配置并返回响应对象
        return Results.success(modelRouteConfigService.toResponse(
                modelRouteConfigService.createRouteConfig(routeId, request)));
    }

    /**
     * 更新模型路由候选配置。
     *
     * @param routeId       模型路由ID
     * @param routeConfigId 模型路由候选配置ID
     * @param request       更新请求
     * @return 更新后的模型路由候选配置
     */
    @PatchMapping("/{routeConfigId}")
    public Result<ModelRouteConfigResponse> updateRouteConfig(@PathVariable Long routeId,
                                                              @PathVariable Long routeConfigId,
                                                              @RequestBody ModelRouteConfigUpdateRequest request) {
        // 1. 更新路由候选配置并返回响应对象
        return Results.success(modelRouteConfigService.toResponse(
                modelRouteConfigService.updateRouteConfig(routeId, routeConfigId, request)));
    }

    /**
     * 删除模型路由候选配置。
     *
     * @param routeId       模型路由ID
     * @param routeConfigId 模型路由候选配置ID
     * @return 删除结果
     */
    @DeleteMapping("/{routeConfigId}")
    public Result<Void> deleteRouteConfig(@PathVariable Long routeId,
                                          @PathVariable Long routeConfigId) {
        // 1. 删除路由候选配置并触发注册表刷新
        modelRouteConfigService.deleteRouteConfig(routeId, routeConfigId);
        return Results.success();
    }
}
