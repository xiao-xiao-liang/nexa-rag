package com.nexarag.model.gateway.embedding;

import com.nexarag.model.enums.ModelBizType;

import java.util.List;

/**
 * Embedding 模型请求。
 *
 * @param traceId  链路追踪ID
 * @param bizType  业务类型
 * @param bizId    业务ID
 * @param routeKey 路由Key
 * @param texts    待向量化文本
 */
public record EmbeddingModelRequest(String traceId, ModelBizType bizType, String bizId,
                                    String routeKey, List<String> texts) {
}
