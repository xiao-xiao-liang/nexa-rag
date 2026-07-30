package com.nexarag.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.model.dto.ModelGovernanceConfigRequest;
import com.nexarag.model.dto.ModelGovernanceConfigResponse;
import com.nexarag.model.entity.ModelGovernanceConfig;

/**
 * 模型治理配置服务，负责单个模型配置的治理参数查询和保存。
 */
public interface ModelGovernanceConfigService extends IService<ModelGovernanceConfig> {

    /**
     * 根据模型配置ID查询治理配置。
     *
     * @param configId 模型配置ID
     * @return 治理配置
     */
    ModelGovernanceConfig getByConfigId(Long configId);

    /**
     * 根据路由标识查询治理配置。
     *
     * @param routeKey 路由标识
     * @return 治理配置，不存在时返回 null
     */
    ModelGovernanceConfig getByRouteKey(String routeKey);

    /**
     * 根据模型配置ID保存治理配置。
     *
     * @param configId 模型配置ID
     * @param request  治理配置保存请求
     * @return 治理配置
     */
    ModelGovernanceConfig saveByConfigId(Long configId, ModelGovernanceConfigRequest request);

    /**
     * 根据路由标识保存治理配置。
     *
     * @param routeKey 路由标识
     * @param request  治理配置局部更新请求
     * @return 保存后的治理配置
     */
    ModelGovernanceConfig saveByRouteKey(String routeKey, ModelGovernanceConfigRequest request);

    /**
     * 迁移路由标识关联的治理配置。
     *
     * @param oldRouteKey 旧路由标识
     * @param newRouteKey 新路由标识
     */
    void renameRouteBinding(String oldRouteKey, String newRouteKey);

    /**
     * 判断模型配置级治理配置是否存在。
     *
     * @param configId 模型配置ID
     * @return true 表示存在
     */
    boolean existsConfigBinding(Long configId);

    /**
     * 判断路由级治理配置是否存在。
     *
     * @param routeKey 路由 key
     * @return true 表示存在
     */
    boolean existsRouteBinding(String routeKey);

    /**
     * 保存默认治理配置，已存在时不覆盖。
     *
     * @param config 默认治理配置
     */
    void saveDefaultIfAbsent(ModelGovernanceConfig config);

    /**
     * 显式恢复默认治理策略。
     *
     * @param governanceId 治理配置ID
     */
    void resetDefault(Long governanceId);

    /**
     * 转换为治理配置响应。
     *
     * @param config 治理配置
     * @return 治理配置响应
     */
    ModelGovernanceConfigResponse toResponse(ModelGovernanceConfig config);
}
