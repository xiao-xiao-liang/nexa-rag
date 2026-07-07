package com.nexarag.model.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
import com.nexarag.model.dto.ModelRouteConfigCreateRequest;
import com.nexarag.model.dto.ModelRouteConfigResponse;
import com.nexarag.model.dto.ModelRouteConfigUpdateRequest;
import com.nexarag.model.entity.ModelRegistryVersion;
import com.nexarag.model.entity.ModelRouteConfig;
import com.nexarag.model.enums.ModelRouteRole;
import com.nexarag.model.mapper.ModelRegistryVersionMapper;
import com.nexarag.model.mapper.ModelRouteConfigMapper;
import com.nexarag.model.refresh.ModelRegistryChangePublisher;
import com.nexarag.model.service.ModelRouteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型路由配置关联服务实现类，负责模型路由与配置关系表基础数据操作。
 */
@Service
@RequiredArgsConstructor
public class ModelRouteConfigServiceImpl extends ServiceImpl<ModelRouteConfigMapper, ModelRouteConfig>
        implements ModelRouteConfigService {

    private static final long INITIAL_REGISTRY_VERSION = 1L;
    private static final long DEFAULT_REGISTRY_VERSION_ID = 1L;
    private static final int DEFAULT_PRIORITY = 0;
    private static final int DEFAULT_WEIGHT = 100;

    private final ModelRegistryVersionMapper modelRegistryVersionMapper;
    private final ModelRegistryChangePublisher modelRegistryChangePublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelRouteConfig createRouteConfig(Long routeId, ModelRouteConfigCreateRequest request) {
        validateRouteId(routeId);
        validateCreateRequest(request);
        if (existsByRouteIdAndConfigId(routeId, request.configId(), null)) {
            throw new ClientException("模型路由候选配置已存在，routeId=" + routeId + "，configId=" + request.configId(),
                    BaseErrorCode.PARAM_ERROR);
        }

        // 1. 构建模型路由候选配置
        ModelRouteConfig routeConfig = ModelRouteConfig.builder()
                .routeConfigId(IdWorker.getId())
                .routeId(routeId)
                .configId(request.configId())
                .role(request.role() == null ? ModelRouteRole.CANDIDATE : request.role())
                .priority(request.priority() == null ? DEFAULT_PRIORITY : request.priority())
                .weight(request.weight() == null ? DEFAULT_WEIGHT : request.weight())
                .enabled(Boolean.TRUE)
                .build();

        // 2. 保存候选配置并发布注册表刷新
        saveRouteConfig(routeConfig);
        bumpRegistryVersionAndPublish();
        return routeConfig;
    }

    @Override
    public List<ModelRouteConfigResponse> listRouteConfigResponses(Long routeId) {
        validateRouteId(routeId);

        // 1. 查询指定路由下的候选配置并转换为响应对象
        return this.lambdaQuery()
                .eq(ModelRouteConfig::getRouteId, routeId)
                .list()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelRouteConfig updateRouteConfig(Long routeId, Long routeConfigId,
                                              ModelRouteConfigUpdateRequest request) {
        validateRouteId(routeId);
        if (request == null) {
            throw new ClientException("模型路由候选配置更新请求不能为空", BaseErrorCode.PARAM_ERROR);
        }

        // 1. 查询已有候选配置
        ModelRouteConfig routeConfig = getRequiredRouteConfig(routeId, routeConfigId);

        // 2. 应用非空更新字段
        if (request.role() != null) {
            routeConfig.setRole(request.role());
        }
        if (request.priority() != null) {
            routeConfig.setPriority(request.priority());
        }
        if (request.weight() != null) {
            routeConfig.setWeight(request.weight());
        }
        if (request.enabled() != null) {
            routeConfig.setEnabled(request.enabled());
        }

        // 3. 更新候选配置并发布注册表刷新
        updateRouteConfigById(routeConfig);
        bumpRegistryVersionAndPublish();
        return routeConfig;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRouteConfig(Long routeId, Long routeConfigId) {
        validateRouteId(routeId);

        // 1. 确认候选配置存在且属于当前路由
        getRequiredRouteConfig(routeId, routeConfigId);

        // 2. 执行逻辑删除并发布注册表刷新
        removeRouteConfigById(routeConfigId);
        bumpRegistryVersionAndPublish();
    }

    @Override
    public boolean existsByConfigId(Long configId) {
        if (configId == null) {
            return false;
        }

        // 1. 查询是否存在引用该模型配置的路由候选
        return this.lambdaQuery()
                .eq(ModelRouteConfig::getConfigId, configId)
                .exists();
    }

    @Override
    public boolean existsByRouteId(Long routeId) {
        if (routeId == null) {
            return false;
        }

        // 1. 查询路由下是否仍存在候选模型配置
        return this.lambdaQuery()
                .eq(ModelRouteConfig::getRouteId, routeId)
                .exists();
    }

    @Override
    public ModelRouteConfigResponse toResponse(ModelRouteConfig routeConfig) {
        if (routeConfig == null) {
            return null;
        }

        // 1. 转换为前端展示使用的模型路由候选配置响应
        return ModelRouteConfigResponse.builder()
                .routeConfigId(routeConfig.getRouteConfigId())
                .routeId(routeConfig.getRouteId())
                .configId(routeConfig.getConfigId())
                .role(routeConfig.getRole())
                .priority(routeConfig.getPriority())
                .weight(routeConfig.getWeight())
                .enabled(routeConfig.getEnabled())
                .createTime(routeConfig.getCreateTime())
                .updateTime(routeConfig.getUpdateTime())
                .build();
    }

    /**
     * 保存模型路由候选配置。
     *
     * @param routeConfig 模型路由候选配置
     * @return true 表示保存成功
     */
    protected boolean saveRouteConfig(ModelRouteConfig routeConfig) {
        return this.save(routeConfig);
    }

    /**
     * 更新模型路由候选配置。
     *
     * @param routeConfig 模型路由候选配置
     * @return true 表示更新成功
     */
    protected boolean updateRouteConfigById(ModelRouteConfig routeConfig) {
        return this.lambdaUpdate()
                .eq(ModelRouteConfig::getRouteConfigId, routeConfig.getRouteConfigId())
                .set(ModelRouteConfig::getRole, routeConfig.getRole())
                .set(ModelRouteConfig::getPriority, routeConfig.getPriority())
                .set(ModelRouteConfig::getWeight, routeConfig.getWeight())
                .set(ModelRouteConfig::getEnabled, routeConfig.getEnabled())
                .update();
    }

    /**
     * 按ID逻辑删除模型路由候选配置。
     *
     * @param routeConfigId 模型路由候选配置ID
     * @return true 表示删除成功
     */
    protected boolean removeRouteConfigById(Long routeConfigId) {
        return this.lambdaUpdate()
                .eq(ModelRouteConfig::getRouteConfigId, routeConfigId)
                .set(ModelRouteConfig::getDelFlag, 1)
                .set(ModelRouteConfig::getDeleteTime, LocalDateTime.now())
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

    private ModelRouteConfig getRequiredRouteConfig(Long routeId, Long routeConfigId) {
        if (routeConfigId == null) {
            throw new ClientException("模型路由候选配置ID不能为空", BaseErrorCode.PARAM_ERROR);
        }
        ModelRouteConfig routeConfig = this.lambdaQuery()
                .eq(ModelRouteConfig::getRouteId, routeId)
                .eq(ModelRouteConfig::getRouteConfigId, routeConfigId)
                .one();
        if (routeConfig == null) {
            throw new ClientException("模型路由候选配置不存在，routeConfigId=" + routeConfigId,
                    BaseErrorCode.PARAM_ERROR);
        }
        return routeConfig;
    }

    private boolean existsByRouteIdAndConfigId(Long routeId, Long configId, Long excludedRouteConfigId) {
        return this.lambdaQuery()
                .eq(ModelRouteConfig::getRouteId, routeId)
                .eq(ModelRouteConfig::getConfigId, configId)
                .ne(excludedRouteConfigId != null, ModelRouteConfig::getRouteConfigId, excludedRouteConfigId)
                .exists();
    }

    private void validateCreateRequest(ModelRouteConfigCreateRequest request) {
        if (request == null) {
            throw new ClientException("模型路由候选配置创建请求不能为空", BaseErrorCode.PARAM_ERROR);
        }
        if (request.configId() == null) {
            throw new ClientException("模型配置ID不能为空", BaseErrorCode.PARAM_ERROR);
        }
    }

    private void validateRouteId(Long routeId) {
        if (routeId == null) {
            throw new ClientException("模型路由ID不能为空", BaseErrorCode.PARAM_ERROR);
        }
    }
}
