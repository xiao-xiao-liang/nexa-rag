package com.nexarag.retrieval.model;

/**
 * 索引运行配置快照，用于把文档处理配置转换为索引阶段可直接使用的稳定配置。
 *
 * @param enabled          是否启用索引阶段外部写入
 * @param vectorEnabled    是否启用向量索引
 * @param keywordEnabled   是否启用关键词索引
 * @param embeddingRouteKey Embedding 模型路由Key
 * @param vectorCollection 向量集合名称
 * @param keywordIndexName 关键词索引名称
 */
public record IndexConfigSnapshot(boolean enabled,
                                  boolean vectorEnabled,
                                  boolean keywordEnabled,
                                  String embeddingRouteKey,
                                  String vectorCollection,
                                  String keywordIndexName) {
}