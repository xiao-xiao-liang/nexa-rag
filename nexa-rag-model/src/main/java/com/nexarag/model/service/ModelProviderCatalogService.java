package com.nexarag.model.service;

import com.nexarag.model.dto.ModelProviderCatalogResponse;

import java.util.List;

/**
 * 模型厂商推荐值服务，负责向前端提供厂商默认地址和推荐模型。
 */
public interface ModelProviderCatalogService {

    /**
     * 查询模型厂商推荐值列表。
     *
     * @return 模型厂商推荐值列表
     */
    List<ModelProviderCatalogResponse> listProviders();
}
