package com.nexarag.model.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.model.dto.ModelConnectionTestRequest;
import com.nexarag.model.dto.ModelConnectionTestResponse;
import com.nexarag.model.service.ModelConnectionTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型配置 Controller，负责模型配置相关 REST 接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model/configs")
public class ModelConfigController {

    private final ModelConnectionTestService modelConnectionTestService;

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
}
