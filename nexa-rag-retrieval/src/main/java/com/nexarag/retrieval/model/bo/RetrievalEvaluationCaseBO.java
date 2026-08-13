package com.nexarag.retrieval.model.bo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 一条人工标注的检索问答回归样本。
 *
 * @param caseId                   样本标识
 * @param question                 用户问题
 * @param expectedRelevantChunkIds 人工确认相关的正文片段标识集合
 */
public record RetrievalEvaluationCaseBO(String caseId, String question, Set<String> expectedRelevantChunkIds) {

    public RetrievalEvaluationCaseBO {
        if (caseId == null || caseId.isBlank() || question == null || question.isBlank()) {
            throw new IllegalArgumentException("检索评测样本必须包含样本标识和问题");
        }
        expectedRelevantChunkIds = expectedRelevantChunkIds == null ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(expectedRelevantChunkIds));
        if (expectedRelevantChunkIds.isEmpty()) {
            throw new IllegalArgumentException("检索评测样本必须至少标注一个相关正文片段");
        }
    }
}
