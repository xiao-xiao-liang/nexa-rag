package com.nexarag.retrieval.model.bo;

/**
 * 检索问答回归集的 Recall@K 评测报告。
 *
 * @param totalCases                 参与评测的样本数
 * @param hitCasesAtK                Top K 内命中任一相关正文的样本数
 * @param totalExpectedRelevantChunks 人工标注的相关正文总数
 * @param recoveredRelevantChunksAtK Top K 内实际召回的相关正文总数
 * @param hitRateAtK                 Top K 命中率
 * @param recallAtK                  Top K 召回率
 */
public record RetrievalEvaluationReportBO(int totalCases, int hitCasesAtK, int totalExpectedRelevantChunks,
                                          int recoveredRelevantChunksAtK, double hitRateAtK, double recallAtK) {
}
