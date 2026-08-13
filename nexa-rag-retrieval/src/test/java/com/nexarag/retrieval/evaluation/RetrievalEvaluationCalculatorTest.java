package com.nexarag.retrieval.evaluation;

import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.model.bo.RetrievalEvaluationCaseBO;
import com.nexarag.retrieval.model.bo.StructureEvaluationCaseBO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 检索和结构评测指标计算测试。 */
class RetrievalEvaluationCalculatorTest {

    private final RetrievalEvaluationCalculator calculator = new RetrievalEvaluationCalculator();

    @Test
    void evaluateRetrievalShouldCalculateRecallAndHitRateAtK() {
        List<RetrievalEvaluationCaseBO> cases = List.of(
                new RetrievalEvaluationCaseBO("case-1", "事务何时提交", Set.of("chunk-1", "chunk-2")),
                new RetrievalEvaluationCaseBO("case-2", "如何创建索引", Set.of("chunk-3")));

        var report = calculator.evaluateRetrieval(cases, 2, evaluationCase -> switch (evaluationCase.caseId()) {
            case "case-1" -> List.of(chunk("chunk-1", 1), chunk("unrelated", 2), chunk("chunk-2", 3));
            case "case-2" -> List.of(chunk("unrelated", 1), chunk("chunk-3", 2));
            default -> List.of();
        });

        assertThat(report.totalCases()).isEqualTo(2);
        assertThat(report.hitCasesAtK()).isEqualTo(2);
        assertThat(report.recoveredRelevantChunksAtK()).isEqualTo(2);
        assertThat(report.totalExpectedRelevantChunks()).isEqualTo(3);
        assertThat(report.hitRateAtK()).isEqualTo(1.0D);
        assertThat(report.recallAtK()).isCloseTo(2.0D / 3.0D, org.assertj.core.data.Offset.offset(0.0001D));
    }

    @Test
    void evaluateStructureShouldCalculateUsabilityAndCompletePathRate() {
        var report = calculator.evaluateStructure(List.of(
                new StructureEvaluationCaseBO("case-1", List.of("事务", "提交"), List.of("事务", "提交")),
                new StructureEvaluationCaseBO("case-2", List.of("索引"), List.of("数据库", "索引")),
                new StructureEvaluationCaseBO("case-3", List.of("锁"), List.of())));

        assertThat(report.totalCases()).isEqualTo(3);
        assertThat(report.usableCases()).isEqualTo(2);
        assertThat(report.completeHeadingPathCases()).isEqualTo(1);
        assertThat(report.structureUsabilityRate()).isCloseTo(2.0D / 3.0D, org.assertj.core.data.Offset.offset(0.0001D));
        assertThat(report.headingPathCompletenessRate()).isCloseTo(1.0D / 3.0D,
                org.assertj.core.data.Offset.offset(0.0001D));
    }

    private RetrievalChunk chunk(String chunkId, int rank) {
        return new RetrievalChunk(chunkId, 1L, rank, null, "测试文档", "LOCAL", "正文", 1.0D, "TEST", rank);
    }
}
