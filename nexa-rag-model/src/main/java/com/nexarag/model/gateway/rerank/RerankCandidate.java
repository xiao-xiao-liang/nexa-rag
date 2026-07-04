package com.nexarag.model.gateway.rerank;

import java.util.Map;

/**
 * 重排序候选内容。
 *
 * @param id       候选ID
 * @param text     候选文本
 * @param metadata 元数据
 */
public record RerankCandidate(String id, String text, Map<String, Object> metadata) {
}
