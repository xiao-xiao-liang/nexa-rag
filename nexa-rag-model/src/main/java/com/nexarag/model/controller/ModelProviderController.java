package com.nexarag.model.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.model.dto.ModelProviderCatalogResponse;
import com.nexarag.model.service.ModelProviderCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型厂商推荐值 Controller。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model/providers")
public class ModelProviderController {

    private final ModelProviderCatalogService modelProviderCatalogService;

    /**
     * 查询模型厂商推荐值列表。
     *
     * @return 模型厂商推荐值列表
     */
    @GetMapping
    public Result<List<ModelProviderCatalogResponse>> listProviders() {
        // 1. 查询内置模型厂商推荐值
        return Results.success(modelProviderCatalogService.listProviders());
    }

    /**
     * 查询模型厂商目录，提供 REST 风格显式路径。
     *
     * @return 模型厂商目录
     */
    @GetMapping("/catalog")
    public Result<List<ModelProviderCatalogResponse>> listProviderCatalog() {
        // 1. 复用模型厂商推荐值查询逻辑
        return listProviders();
    }
}
