package com.nexarag.retrieval.evaluation;

import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.model.bo.RetrievalEvaluationCaseBO;
import com.nexarag.retrieval.model.bo.RetrievalEvaluationReportBO;
import com.nexarag.retrieval.model.bo.StructureEvaluationCaseBO;
import com.nexarag.retrieval.model.bo.StructureEvaluationReportBO;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 文档结构优化前后的检索和标题路径评测指标计算器。
 */
@Component
public class RetrievalEvaluationCalculator {

    /**
     * 以传入的检索函数计算问答回归集的 Recall@K 和 HitRate@K。
     *
     * @param cases     人工标注问答样本
     * @param topK      每个问题纳入评测的返回结果数
     * @param retriever 按样本执行实际检索的函数
     * @return 检索评测报告
     */
    public RetrievalEvaluationReportBO evaluateRetrieval(List<RetrievalEvaluationCaseBO> cases, int topK,
                                                          Function<RetrievalEvaluationCaseBO, List<RetrievalChunk>> retriever) {
        if (topK <= 0) {
            throw new IllegalArgumentException("检索评测的Top K必须大于0");
        }
        if (retriever == null) {
            throw new IllegalArgumentException("检索评测必须提供检索执行函数");
        }
        List<RetrievalEvaluationCaseBO> evaluationCases = cases == null ? List.of() : cases;
        int hitCases = 0;
        int expectedCount = 0;
        int recoveredCount = 0;
        for (RetrievalEvaluationCaseBO evaluationCase : evaluationCases) {
            Set<String> expectedIds = evaluationCase.expectedRelevantChunkIds();
            expectedCount += expectedIds.size();
            Set<String> retrievedIds = topKChunkIds(retriever.apply(evaluationCase), topK);
            long recovered = expectedIds.stream().filter(retrievedIds::contains).count();
            if (recovered > 0) {
                hitCases++;
            }
            recoveredCount += (int) recovered;
        }
        return new RetrievalEvaluationReportBO(evaluationCases.size(), hitCases, expectedCount, recoveredCount,
                ratio(hitCases, evaluationCases.size()), ratio(recoveredCount, expectedCount));
    }

    /**
     * 计算标题路径的可用率和完整率。
     *
     * @param cases 人工标注的路径样本
     * @return 结构评测报告
     */
    public StructureEvaluationReportBO evaluateStructure(List<StructureEvaluationCaseBO> cases) {
        List<StructureEvaluationCaseBO> evaluationCases = cases == null ? List.of() : cases;
        int usableCases = 0;
        int completeCases = 0;
        for (StructureEvaluationCaseBO evaluationCase : evaluationCases) {
            if (!evaluationCase.actualHeadingPath().isEmpty()) {
                usableCases++;
            }
            if (evaluationCase.expectedHeadingPath().equals(evaluationCase.actualHeadingPath())) {
                completeCases++;
            }
        }
        return new StructureEvaluationReportBO(evaluationCases.size(), usableCases, completeCases,
                ratio(usableCases, evaluationCases.size()), ratio(completeCases, evaluationCases.size()));
    }

    private Set<String> topKChunkIds(List<RetrievalChunk> chunks, int topK) {
        if (chunks == null || chunks.isEmpty()) {
            return Set.of();
        }
        Set<String> chunkIds = new HashSet<>();
        chunks.stream().limit(topK).map(RetrievalChunk::chunkId)
                .filter(chunkId -> chunkId != null && !chunkId.isBlank())
                .forEach(chunkIds::add);
        return chunkIds;
    }

    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0D : (double) numerator / denominator;
    }
}
