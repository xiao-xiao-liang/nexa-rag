package com.nexarag.document.dto;

import jakarta.validation.Valid;

/**
 * 文档处理请求。
 *
 * @param splitConfig 切分配置
 */
public record ProcessDocumentRequest(@Valid SplitConfigRequest splitConfig) {
}
