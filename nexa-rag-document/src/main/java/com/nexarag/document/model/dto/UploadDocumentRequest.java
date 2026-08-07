package com.nexarag.document.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * 上传文档请求，承载文档元信息和本次处理配置。
 *
 * @param title       文档标题
 * @param description 文档描述
 * @param splitConfig 切分配置
 * @param parseConfig 解析配置
 * @param indexConfig 索引配置
 */
public record UploadDocumentRequest(
        @Size(max = 256, message = "文档标题不能超过256个字符")
        String title,
        @Size(max = 1024, message = "文档描述不能超过1024个字符")
        String description,
        @Valid SplitConfigRequest splitConfig,
        @Valid ParseConfigRequest parseConfig,
        @Valid IndexConfigRequest indexConfig) {
}
