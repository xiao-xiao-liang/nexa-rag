package com.nexarag.model.dto;

import lombok.Builder;

/**
 * 模型注册表快照响应，用于展示当前 JVM 内存中的注册表概要。
 *
 * @param versionNo             注册表版本号
 * @param configCount           模型配置数量
 * @param routeCount            模型路由数量
 * @param routeConfigCount      模型路由候选配置数量
 * @param governanceConfigCount 模型治理配置数量
 */
@Builder
public record ModelRegistrySnapshotResponse(long versionNo, int configCount, int routeCount,
                                            int routeConfigCount, int governanceConfigCount) {
}
