package com.nexarag.document.model.dto;

import jakarta.validation.Valid;

/**
 * 文档处理请求。
 *
 * @param splitConfig 切分配置
 * @param parseConfig 解析配置
 * @param indexConfig 索引配置
 */
public record ProcessDocumentRequest(@Valid SplitConfigRequest splitConfig,
                                     @Valid ParseConfigRequest parseConfig,
                                     @Valid IndexConfigRequest indexConfig) {

    /**
     * 兼容阶段二仅传切分配置的构造方式。
     *
     * @param splitConfig 切分配置
     */
    public ProcessDocumentRequest(SplitConfigRequest splitConfig) {
        this(splitConfig, null, null);
    }
}
