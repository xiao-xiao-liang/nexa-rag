package com.nexarag.model.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.model.dto.ModelConnectionTestRequest;
import com.nexarag.model.dto.ModelConnectionTestResponse;
import com.nexarag.model.dto.ModelConfigCreateRequest;
import com.nexarag.model.dto.ModelConfigResponse;
import com.nexarag.model.dto.ModelConfigUpdateRequest;
import com.nexarag.model.dto.ModelGovernanceConfigRequest;
import com.nexarag.model.dto.ModelGovernanceConfigResponse;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.service.ModelConfigService;
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
 * 模型配置 Controller，负责模型配置相关 REST 接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model/configs")
public class ModelConfigController {

    private final ModelConfigService modelConfigService;
    private final ModelConnectionTestService modelConnectionTestService;
    private final ModelGovernanceConfigService modelGovernanceConfigService;

    /**
     * 查询模型配置列表。
     *
     * @return 模型配置列表
     */
    @GetMapping
    public Result<List<ModelConfigResponse>> listConfigs() {
        // 1. 查询模型配置脱敏列表
        return Results.success(modelConfigService.listConfigResponses());
    }

    /**
     * 查询模型配置详情。
     *
     * @param configId 模型配置ID
     * @return 模型配置详情
     */
    @GetMapping("/{configId}")
    public Result<ModelConfigResponse> getConfig(@PathVariable Long configId) {
        // 1. 查询模型配置脱敏详情
        return Results.success(modelConfigService.getConfigResponse(configId));
    }

    /**
     * 查询未加掩码的模型原始 API Key。
     *
     * @param configId 模型配置ID
     * @return 原始 API Key 明文
     */
    @GetMapping("/{configId}/raw-key")
    public Result<String> getRawApiKey(@PathVariable Long configId) {
        // 1. 查询未脱敏模型原始 API Key
        return Results.success(modelConfigService.getRawApiKey(configId));
    }

    /**
     * 创建模型配置。
     *
     * @param request 创建请求
     * @return 创建后的模型配置
     */
    @PostMapping
    public Result<ModelConfigResponse> createConfig(@RequestBody ModelConfigCreateRequest request) {
        // 1. 创建模型配置并返回脱敏详情
        return Results.success(modelConfigService.getConfigResponse(modelConfigService.createConfig(request).getConfigId()));
    }

    /**
     * 更新模型配置。
     *
     * @param configId 模型配置ID
     * @param request  更新请求
     * @return 更新后的模型配置
     */
    @PutMapping("/{configId}")
    public Result<ModelConfigResponse> updateConfig(@PathVariable Long configId,
                                                    @RequestBody ModelConfigUpdateRequest request) {
        // 1. 更新模型配置并返回脱敏详情
        return Results.success(modelConfigService.getConfigResponse(modelConfigService.updateConfig(configId, request)
                .getConfigId()));
    }

    /**
     * 局部更新模型配置。
     *
     * @param configId 模型配置ID
     * @param request  更新请求
     * @return 更新后的模型配置
     */
    @PatchMapping("/{configId}")
    public Result<ModelConfigResponse> patchConfig(@PathVariable Long configId,
                                                   @RequestBody ModelConfigUpdateRequest request) {
        // 1. 复用模型配置更新逻辑，按非空字段局部更新
        return updateConfig(configId, request);
    }

    /**
     * 删除模型配置。
     *
     * @param configId 模型配置ID
     * @return 删除结果
     */
    @DeleteMapping("/{configId}")
    public Result<Void> deleteConfig(@PathVariable Long configId) {
        // 1. 删除模型配置并触发注册表刷新
        modelConfigService.deleteConfig(configId);
        return Results.success();
    }

    /**
     * 测试模型配置连接。
     *
     * @param configId 模型配置ID
     * @param request  测试请求
     * @return 测试结果
     */
    @PostMapping("/{configId}/test")
    public Result<ModelConnectionTestResponse> testConfig(@PathVariable Long configId,
                                                          @RequestBody(required = false)
                                                          ModelConnectionTestRequest request) {
        // 1. 委托模型连接测试服务执行配置测试
        return Results.success(modelConnectionTestService.testConfig(configId, request));
    }

    /**
     * 测试模型配置连接，提供 REST 风格路径。
     *
     * @param configId 模型配置ID
     * @param request  测试请求
     * @return 测试结果
     */
    @PostMapping("/{configId}/connection-tests")
    public Result<ModelConnectionTestResponse> createConfigConnectionTest(@PathVariable Long configId,
                                                                          @RequestBody(required = false)
                                                                          ModelConnectionTestRequest request) {
        // 1. 复用模型配置连接测试逻辑
        return testConfig(configId, request);
    }

    /**
     * 查询模型治理配置。
     *
     * @param configId 模型配置ID
     * @return 模型治理配置
     */
    @GetMapping("/{configId}/governance")
    public Result<ModelGovernanceConfigResponse> getGovernance(@PathVariable Long configId) {
        // 1. 查询模型治理配置并转换为响应对象
        ModelGovernanceConfig config = modelGovernanceConfigService.getByConfigId(configId);
        return Results.success(modelGovernanceConfigService.toResponse(config));
    }

    /**
     * 保存模型治理配置。
     *
     * @param configId 模型配置ID
     * @param request  模型治理配置保存请求
     * @return 保存后的模型治理配置
     */
    @PutMapping("/{configId}/governance")
    public Result<ModelGovernanceConfigResponse> saveGovernance(@PathVariable Long configId,
                                                                @RequestBody
                                                                ModelGovernanceConfigRequest request) {
        // 1. 保存模型治理配置并转换为响应对象
        ModelGovernanceConfig config = modelGovernanceConfigService.saveByConfigId(configId, request);
        return Results.success(modelGovernanceConfigService.toResponse(config));
    }
}
