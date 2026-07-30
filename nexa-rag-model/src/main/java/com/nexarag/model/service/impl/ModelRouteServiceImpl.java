package com.nexarag.model.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
import com.nexarag.model.config.ModelGovernanceProperties;
import com.nexarag.model.dto.ModelRouteCreateRequest;
import com.nexarag.model.dto.ModelRouteResponse;
import com.nexarag.model.dto.ModelRouteUpdateRequest;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.entity.ModelRegistryVersion;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.governance.DefaultModelGovernancePolicyFactory;
import com.nexarag.model.mapper.ModelRouteMapper;
import com.nexarag.model.mapper.ModelRegistryVersionMapper;
import com.nexarag.model.refresh.ModelRegistryChangePublisher;
import com.nexarag.model.service.ModelGovernanceConfigService;
import com.nexarag.model.service.ModelRouteConfigService;
import com.nexarag.model.service.ModelRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型路由服务实现类，负责模型路由表基础数据操作。
 */
@Service
@RequiredArgsConstructor
public class ModelRouteServiceImpl extends ServiceImpl<ModelRouteMapper, ModelRoute>
        implements ModelRouteService {

    private static final long INITIAL_REGISTRY_VERSION = 1L;
    private static final long DEFAULT_REGISTRY_VERSION_ID = 1L;

    private final ModelRegistryVersionMapper modelRegistryVersionMapper;
    private final ModelRegistryChangePublisher modelRegistryChangePublisher;
    private final DefaultModelGovernancePolicyFactory defaultModelGovernancePolicyFactory;
    private final ModelGovernanceConfigService modelGovernanceConfigService;
    private final ModelGovernanceProperties modelGovernanceProperties;
    private final ModelRouteConfigService modelRouteConfigService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelRoute createRoute(ModelRouteCreateRequest request) {
        validateCreateRequest(request);
        if (existsByRouteKey(request.routeKey(), null)) {
            throw new ClientException("模型路由标识已存在，routeKey=" + request.routeKey(), BaseErrorCode.PARAM_ERROR);
        }

        // 1. 构建模型路由实体
        ModelRoute route = ModelRoute.builder()
                .routeId(IdWorker.getId())
                .routeKey(request.routeKey())
                .modelType(request.modelType())
                .strategy(request.strategy())
                .enabled(Boolean.TRUE)
                .remark(request.remark())
                .build();

        // 2. 保存模型路由
        saveRoute(route);

        // 3. 自动创建路由级默认治理配置，便于切换 ROUTE 模式后直接生效
        autoCreateDefaultGovernance(route);

        // 4. 触发模型注册表刷新
        bumpRegistryVersionAndPublish();
        return route;
    }

    @Override
    public List<ModelRouteResponse> listRouteResponses() {
        // 1. 查询模型路由列表并转换为响应对象
        return this.list().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ModelRouteResponse getRouteResponse(Long routeId) {
        // 1. 查询模型路由详情并转换为响应对象
        return toResponse(getRequiredRoute(routeId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelRoute updateRoute(Long routeId, ModelRouteUpdateRequest request) {
        if (request == null) {
            throw new ClientException("模型路由更新请求不能为空", BaseErrorCode.PARAM_ERROR);
        }

        // 1. 查询已有模型路由
        ModelRoute route = getRequiredRoute(routeId);
        String oldRouteKey = route.getRouteKey();
        String routeKey = StringUtils.hasText(request.routeKey()) ? request.routeKey() : route.getRouteKey();
        if (!route.getRouteKey().equals(routeKey) && existsByRouteKey(routeKey, routeId)) {
            throw new ClientException("模型路由标识已存在，routeKey=" + routeKey, BaseErrorCode.PARAM_ERROR);
        }

        // 2. 应用非空更新字段
        route.setRouteKey(routeKey);
        if (request.modelType() != null) {
            route.setModelType(request.modelType());
        }
        if (request.strategy() != null) {
            route.setStrategy(request.strategy());
        }
        if (request.enabled() != null) {
            route.setEnabled(request.enabled());
        }
        if (request.remark() != null) {
            route.setRemark(request.remark());
        }

        // 3. 路由标识变更时迁移关联路由级治理配置
        if (!oldRouteKey.equals(routeKey)) {
            modelGovernanceConfigService.renameRouteBinding(oldRouteKey, routeKey);
        }

        // 4. 更新模型路由并发布注册表刷新
        updateRouteById(route);
        bumpRegistryVersionAndPublish();
        return route;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoute(Long routeId) {
        if (routeId == null) {
            throw new ClientException("模型路由ID不能为空", BaseErrorCode.PARAM_ERROR);
        }

        // 1. 确认模型路由存在
        getRequiredRoute(routeId);

        // 2. 禁止删除仍包含候选模型的路由
        if (modelRouteConfigService.existsByRouteId(routeId)) {
            throw new ClientException("模型路由仍存在候选配置，请先移除路由下的模型配置", BaseErrorCode.PARAM_ERROR);
        }

        // 3. 执行逻辑删除并记录删除时间
        removeRouteById(routeId);

        // 4. 触发模型注册表刷新
        bumpRegistryVersionAndPublish();
    }

    @Override
    public ModelRouteResponse toResponse(ModelRoute route) {
        if (route == null) {
            return null;
        }

        // 1. 转换为前端展示使用的模型路由响应
        return ModelRouteResponse.builder()
                .routeId(route.getRouteId())
                .routeKey(route.getRouteKey())
                .modelType(route.getModelType())
                .strategy(route.getStrategy())
                .enabled(route.getEnabled())
                .remark(route.getRemark())
                .createTime(route.getCreateTime())
                .updateTime(route.getUpdateTime())
                .build();
    }

    /**
     * 判断模型路由标识是否已存在。
     *
     * @param routeKey        模型路由标识
     * @param excludedRouteId 需要排除的模型路由ID
     * @return true 表示已存在
     */
    protected boolean existsByRouteKey(String routeKey, Long excludedRouteId) {
        return this.lambdaQuery()
                .eq(ModelRoute::getRouteKey, routeKey)
                .ne(excludedRouteId != null, ModelRoute::getRouteId, excludedRouteId)
                .exists();
    }

    /**
     * 保存模型路由。
     *
     * @param route 模型路由
     * @return true 表示保存成功
     */
    protected boolean saveRoute(ModelRoute route) {
        return this.save(route);
    }

    /**
     * 按ID更新模型路由。
     *
     * @param route 模型路由
     * @return true 表示更新成功
     */
    protected boolean updateRouteById(ModelRoute route) {
        return this.lambdaUpdate()
                .eq(ModelRoute::getRouteId, route.getRouteId())
                .set(ModelRoute::getRouteKey, route.getRouteKey())
                .set(ModelRoute::getModelType, route.getModelType())
                .set(ModelRoute::getStrategy, route.getStrategy())
                .set(ModelRoute::getEnabled, route.getEnabled())
                .set(ModelRoute::getRemark, route.getRemark())
                .update();
    }

    /**
     * 根据ID查询模型路由，不存在时抛出异常。
     *
     * @param routeId 模型路由ID
     * @return 模型路由
     */
    protected ModelRoute getRequiredRoute(Long routeId) {
        ModelRoute route = this.getById(routeId);
        if (route == null) {
            throw new ClientException("模型路由不存在，routeId=" + routeId, BaseErrorCode.PARAM_ERROR);
        }
        return route;
    }

    /**
     * 按ID逻辑删除模型路由。
     *
     * @param routeId 模型路由ID
     * @return true 表示删除成功
     */
    protected boolean removeRouteById(Long routeId) {
        return this.lambdaUpdate()
                .eq(ModelRoute::getRouteId, routeId)
                .set(ModelRoute::getDelFlag, 1)
                .set(ModelRoute::getDeleteTime, LocalDateTime.now())
                .update();
    }

    /**
     * 递增模型注册表版本并发布刷新消息。
     *
     * @return 最新模型注册表版本号
     */
    protected long bumpRegistryVersionAndPublish() {
        ModelRegistryVersion version = modelRegistryVersionMapper.selectById(DEFAULT_REGISTRY_VERSION_ID);
        long nextVersionNo = version == null ? INITIAL_REGISTRY_VERSION : version.getVersionNo() + 1;

        // 1. 写入最新模型注册表版本
        ModelRegistryVersion nextVersion = new ModelRegistryVersion();
        nextVersion.setVersionId(DEFAULT_REGISTRY_VERSION_ID);
        nextVersion.setVersionNo(nextVersionNo);
        if (version == null) {
            modelRegistryVersionMapper.insert(nextVersion);
        } else {
            modelRegistryVersionMapper.updateById(nextVersion);
        }

        // 2. 发布模型注册表刷新消息
        modelRegistryChangePublisher.publish(nextVersionNo);
        return nextVersionNo;
    }

    private void autoCreateDefaultGovernance(ModelRoute route) {
        if (modelGovernanceProperties == null
                || !Boolean.TRUE.equals(modelGovernanceProperties.getGovernance().getAutoCreateDefault())) {
            return;
        }

        // 1. 按路由模型类型生成默认治理配置
        ModelGovernanceConfig governanceConfig =
                defaultModelGovernancePolicyFactory.createForRoute(route.getRouteKey(), route.getModelType());

        // 2. 保存默认治理配置，已存在时不覆盖
        modelGovernanceConfigService.saveDefaultIfAbsent(governanceConfig);
    }

    private void validateCreateRequest(ModelRouteCreateRequest request) {
        if (request == null) {
            throw new ClientException("模型路由创建请求不能为空", BaseErrorCode.PARAM_ERROR);
        }
        if (!StringUtils.hasText(request.routeKey())) {
            throw new ClientException("模型路由标识不能为空", BaseErrorCode.PARAM_ERROR);
        }
        if (request.modelType() == null) {
            throw new ClientException("模型类型不能为空", BaseErrorCode.PARAM_ERROR);
        }
        if (request.strategy() == null) {
            throw new ClientException("模型路由策略不能为空", BaseErrorCode.PARAM_ERROR);
        }
    }
}
