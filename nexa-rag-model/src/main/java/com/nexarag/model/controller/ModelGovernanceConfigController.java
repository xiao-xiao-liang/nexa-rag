package com.nexarag.model.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.model.dto.ModelGovernanceConfigResponse;
import com.nexarag.model.service.ModelGovernanceConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型治理配置 Controller，负责模型治理参数的管理类 REST 接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model/governance-configs")
public class ModelGovernanceConfigController {

    private final ModelGovernanceConfigService modelGovernanceConfigService;

    /**
     * 查询模型治理配置列表。
     *
     * @return 模型治理配置列表
     */
    @GetMapping
    public Result<List<ModelGovernanceConfigResponse>> listGovernanceConfigs() {
        // 1. 查询治理配置列表并转换为响应对象
        return Results.success(modelGovernanceConfigService.list().stream()
                .map(modelGovernanceConfigService::toResponse)
                .toList());
    }

    /**
     * 重置模型治理配置为默认值。
     *
     * @param governanceId 模型治理配置ID
     * @return 重置结果
     */
    @PostMapping("/{governanceId}/reset-default")
    public Result<Void> resetDefault(@PathVariable Long governanceId) {
        // 1. 重置治理配置为系统默认值
        modelGovernanceConfigService.resetDefault(governanceId);
        return Results.success();
    }
}
