package com.nexarag.model.gateway.rerank;

import com.nexarag.model.enums.ModelBizType;

import java.util.List;

/**
 * 重排序模型请求。
 *
 * @param traceId    链路追踪ID
 * @param bizType    业务类型
 * @param bizId      业务ID
 * @param routeKey   路由Key
 * @param query      查询文本
 * @param candidates 候选内容
 */
public record RerankModelRequest(String traceId, ModelBizType bizType, String bizId,
                                 String routeKey, String query, List<RerankCandidate> candidates) {
}
