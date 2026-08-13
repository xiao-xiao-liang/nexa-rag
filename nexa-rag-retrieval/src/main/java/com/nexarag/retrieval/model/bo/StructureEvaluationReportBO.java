package com.nexarag.retrieval.model.bo;

/**
 * 文档结构恢复的可用性和标题路径完整率报告。
 *
 * @param totalCases                  参与评测的样本数
 * @param usableCases                 拥有非空标题路径的样本数
 * @param completeHeadingPathCases    标题路径与人工标注完全一致的样本数
 * @param structureUsabilityRate      结构可用率
 * @param headingPathCompletenessRate 标题路径完整率
 */
public record StructureEvaluationReportBO(int totalCases, int usableCases, int completeHeadingPathCases,
                                          double structureUsabilityRate, double headingPathCompletenessRate) {
}
