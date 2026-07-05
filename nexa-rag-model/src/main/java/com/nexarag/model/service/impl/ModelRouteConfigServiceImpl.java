package com.nexarag.model.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.model.entity.ModelRouteConfig;
import com.nexarag.model.mapper.ModelRouteConfigMapper;
import com.nexarag.model.service.ModelRouteConfigService;
import org.springframework.stereotype.Service;

/**
 * 模型路由配置关联服务实现类，负责模型路由与配置关系表基础数据操作。
 */
@Service
public class ModelRouteConfigServiceImpl extends ServiceImpl<ModelRouteConfigMapper, ModelRouteConfig>
        implements ModelRouteConfigService {
}
