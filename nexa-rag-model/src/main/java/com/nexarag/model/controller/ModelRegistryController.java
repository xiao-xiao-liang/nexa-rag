package com.nexarag.model.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.model.dto.ModelRegistrySnapshotResponse;
import com.nexarag.model.registry.ModelRegistry;
import com.nexarag.model.registry.ModelRegistryRefresher;
import com.nexarag.model.registry.ModelRegistrySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型注册表 Controller，负责当前 JVM 注册表快照查询和手动刷新接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model/registry")
public class ModelRegistryController {

    private final ModelRegistry modelRegistry;
    private final ModelRegistryRefresher modelRegistryRefresher;

    /**
     * 手动刷新当前 JVM 的模型注册表快照。
     *
     * @return true 表示本次执行了刷新
     */
    @PostMapping("/refresh")
    public Result<Boolean> refresh() {
        // 1. 按数据库当前版本刷新本地模型注册表
        return Results.success(modelRegistryRefresher.refreshCurrentVersion());
    }

    /**
     * 查询当前 JVM 的模型注册表快照概要。
     *
     * @return 模型注册表快照概要
     */
    @GetMapping("/snapshot")
    public Result<ModelRegistrySnapshotResponse> getSnapshot() {
        // 1. 读取当前 JVM 内存快照并返回概要信息
        ModelRegistrySnapshot snapshot = modelRegistry.current();
        int routeConfigCount = snapshot.routeConfigMap().values().stream()
                .mapToInt(java.util.List::size)
                .sum();
        return Results.success(ModelRegistrySnapshotResponse.builder()
                .versionNo(snapshot.versionNo())
                .configCount(snapshot.configMap().size())
                .routeCount(snapshot.routeMap().size())
                .routeConfigCount(routeConfigCount)
                .governanceConfigCount(snapshot.governanceConfigMap().size())
                .build());
    }
}
