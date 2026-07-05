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
 * 模型路由 Controller，负责模型路由相关 REST 接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model/routes")
public class ModelRouteController {

    private final ModelConnectionTestService modelConnectionTestService;

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
}
