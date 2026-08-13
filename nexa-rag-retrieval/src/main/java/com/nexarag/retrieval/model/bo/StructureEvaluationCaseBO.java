package com.nexarag.retrieval.model.bo;

import java.util.List;

/**
 * 一条标题路径结构回归样本。
 *
 * @param caseId              样本标识
 * @param expectedHeadingPath 人工确认的完整标题路径
 * @param actualHeadingPath   当前切分结果中的标题路径
 */
public record StructureEvaluationCaseBO(String caseId, List<String> expectedHeadingPath,
                                        List<String> actualHeadingPath) {

    public StructureEvaluationCaseBO {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("结构评测样本必须包含样本标识");
        }
        expectedHeadingPath = expectedHeadingPath == null ? List.of() : List.copyOf(expectedHeadingPath);
        actualHeadingPath = actualHeadingPath == null ? List.of() : List.copyOf(actualHeadingPath);
        if (expectedHeadingPath.isEmpty()) {
            throw new IllegalArgumentException("结构评测样本必须包含人工确认的标题路径");
        }
    }
}
