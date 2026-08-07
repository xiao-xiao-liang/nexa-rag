package com.nexarag.infra.alert.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.infra.alert.AlertTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 告警渠道连通性测试接口，仅在告警总开关开启时提供。
 */
@RestController
@RequestMapping("/api/alert-tests")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.alert", name = "enabled", havingValue = "true")
public class AlertTestController {

    private final AlertTestService alertTestService;

    /**
     * 向指定渠道发送测试告警。
     *
     * @param channel 告警渠道名称，支持 feishu 和 email
     * @return 不包含渠道配置的成功响应
     */
    @PostMapping("/{channel}")
    public Result<Void> send(@PathVariable String channel) {
        // 1. 委托服务解析渠道并通过真实发送链路投递测试消息
        alertTestService.send(channel);

        // 2. 返回不包含任何渠道配置的统一成功响应
        return Results.success();
    }
}
