package com.nexarag.model.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.mapper.ModelRouteMapper;
import com.nexarag.model.service.ModelRouteService;
import org.springframework.stereotype.Service;

/**
 * 模型路由服务实现类，负责模型路由表基础数据操作。
 */
@Service
public class ModelRouteServiceImpl extends ServiceImpl<ModelRouteMapper, ModelRoute>
        implements ModelRouteService {
}
