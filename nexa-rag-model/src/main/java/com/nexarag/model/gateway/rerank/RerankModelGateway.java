package com.nexarag.model.gateway.rerank;

/**
 * 重排序模型网关。
 */
public interface RerankModelGateway {

    /**
     * 调用重排序模型。
     *
     * @param request 重排序模型请求
     * @return 重排序模型响应
     */
    RerankModelResponse rerank(RerankModelRequest request);
}
