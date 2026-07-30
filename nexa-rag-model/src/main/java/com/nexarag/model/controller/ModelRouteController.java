package com.nexarag.model.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.model.dto.ModelConnectionTestRequest;
import com.nexarag.model.dto.ModelConnectionTestResponse;
import com.nexarag.model.dto.ModelGovernanceConfigRequest;
import com.nexarag.model.dto.ModelGovernanceConfigResponse;
import com.nexarag.model.dto.ModelRouteCreateRequest;
import com.nexarag.model.dto.ModelRouteResponse;
import com.nexarag.model.dto.ModelRouteUpdateRequest;
import com.nexarag.model.service.ModelRouteService;
import com.nexarag.model.service.ModelConnectionTestService;
import com.nexarag.model.service.ModelGovernanceConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型路由 Controller，负责模型路由相关 REST 接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model/routes")
public class ModelRouteController {

    private final ModelRouteService modelRouteService;
    private final ModelConnectionTestService modelConnectionTestService;
    private final ModelGovernanceConfigService modelGovernanceConfigService;

    /**
     * 查询模型路由列表。
     *
     * @return 模型路由列表
     */
    @GetMapping
    public Result<List<ModelRouteResponse>> listRoutes() {
        // 1. 查询模型路由列表
        return Results.success(modelRouteService.listRouteResponses());
    }

    /**
     * 查询模型路由详情。
     *
     * @param routeId 模型路由ID
     * @return 模型路由详情
     */
    @GetMapping("/{routeId}")
    public Result<ModelRouteResponse> getRoute(@PathVariable Long routeId) {
        // 1. 查询模型路由详情
        return Results.success(modelRouteService.getRouteResponse(routeId));
    }

    /**
     * 创建模型路由。
     *
     * @param request 创建请求
     * @return 创建后的模型路由
     */
    @PostMapping
    public Result<ModelRouteResponse> createRoute(@RequestBody ModelRouteCreateRequest request) {
        // 1. 创建模型路由并返回响应对象
        return Results.success(modelRouteService.toResponse(modelRouteService.createRoute(request)));
    }

    /**
     * 更新模型路由。
     *
     * @param routeId 模型路由ID
     * @param request 更新请求
     * @return 更新后的模型路由
     */
    @PatchMapping("/{routeId}")
    public Result<ModelRouteResponse> updateRoute(@PathVariable Long routeId,
                                                  @RequestBody ModelRouteUpdateRequest request) {
        // 1. 更新模型路由并返回响应对象
        return Results.success(modelRouteService.toResponse(modelRouteService.updateRoute(routeId, request)));
    }

    /**
     * 查询路由级模型治理配置。
     *
     * @param routeId 模型路由ID
     * @return 路由级治理配置，不存在时 data 为 null
     */
    @GetMapping("/{routeId}/governance")
    public Result<ModelGovernanceConfigResponse> getGovernance(@PathVariable Long routeId) {
        // 1. 根据稳定路由ID查询当前路由标识
        ModelRouteResponse route = modelRouteService.getRouteResponse(routeId);

        // 2. 查询并转换路由级治理配置
        return Results.success(modelGovernanceConfigService.toResponse(
                modelGovernanceConfigService.getByRouteKey(route.routeKey())));
    }

    /**
     * 保存路由级模型治理配置。
     *
     * @param routeId 模型路由ID
     * @param request 治理配置局部更新请求
     * @return 保存后的路由级治理配置
     */
    @PutMapping("/{routeId}/governance")
    public Result<ModelGovernanceConfigResponse> saveGovernance(@PathVariable Long routeId,
                                                                @RequestBody ModelGovernanceConfigRequest request) {
        // 1. 根据稳定路由ID查询当前路由标识
        ModelRouteResponse route = modelRouteService.getRouteResponse(routeId);

        // 2. 保存并转换路由级治理配置
        return Results.success(modelGovernanceConfigService.toResponse(
                modelGovernanceConfigService.saveByRouteKey(route.routeKey(), request)));
    }

    /**
     * 删除模型路由。
     *
     * @param routeId 模型路由ID
     * @return 删除结果
     */
    @DeleteMapping("/{routeId}")
    public Result<Void> deleteRoute(@PathVariable Long routeId) {
        // 1. 删除模型路由并触发注册表刷新
        modelRouteService.deleteRoute(routeId);
        return Results.success();
    }

    /**
     * 测试模型路由连接。
     *
     * @param routeId 模型路由ID
     * @param request 测试请求
     * @return 测试结果
     */
    @PostMapping("/{routeId}/test")
    public Result<ModelConnectionTestResponse> testRoute(@PathVariable Long routeId,
                                                         @RequestBody(required = false)
                                                         ModelConnectionTestRequest request) {
        // 1. 委托模型连接测试服务执行路由测试
        return Results.success(modelConnectionTestService.testRoute(routeId, request));
    }

    /**
     * 测试模型路由连接，提供 REST 风格路径。
     *
     * @param routeId 模型路由ID
     * @param request 测试请求
     * @return 测试结果
     */
    @PostMapping("/{routeId}/connection-tests")
    public Result<ModelConnectionTestResponse> createRouteConnectionTest(@PathVariable Long routeId,
                                                                         @RequestBody(required = false)
                                                                         ModelConnectionTestRequest request) {
        // 1. 复用模型路由连接测试逻辑
        return testRoute(routeId, request);
    }
}
