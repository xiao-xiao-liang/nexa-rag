package com.nexarag.document.model.dto;

/**
 * 文档索引配置请求，描述本次处理是否写入检索索引。
 *
 * @param enabled        是否启用索引
 * @param vectorEnabled  是否启用向量索引
 * @param keywordEnabled 是否启用关键词索引
 */
public record IndexConfigRequest(Boolean enabled,
                                 Boolean vectorEnabled,
                                 Boolean keywordEnabled) {
}
