package com.nexarag.document.dto;

/**
 * 文档处理请求。
 *
 * @param splitConfig 切分配置
 */
public record ProcessDocumentRequest(SplitConfigRequest splitConfig) {
}
