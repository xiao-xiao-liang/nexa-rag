package com.nexarag.workflow.service;

import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.route.ModelRouteContext;
import com.nexarag.model.route.ModelRouteDecision;
import com.nexarag.model.route.ModelRoutePlan;
import com.nexarag.model.route.ModelRouter;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.retriever.ParentContextExpansionRetriever;
import com.nexarag.workflow.config.ModelInputEvidenceProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 模型输入窗口证据选择器测试。 */
class ModelInputEvidenceSelectorTest {

    @Test
    void shouldFallbackToDirectRerankedChildrenWhenFullParentDoesNotFit() {
        ModelInputEvidenceSelector selector = selector(router(1024, 4));
        RetrievalChunk parent = chunk("parent", null, "完整父正文".repeat(600), ParentContextExpansionRetriever.PARENT_CONTEXT_CHANNEL);
        RetrievalChunk directHit = chunk("child-1", "parent", "命中正文", "RERANK");
        RetrievalChunk sibling = chunk("child-2", "parent", "未命中兄弟正文", "RERANK");

        List<RetrievalChunk> selected = selector.select(List.of(parent), List.of(directHit), 0, "answer");

        assertThat(selected).containsExactly(directHit);
        assertThat(selected).doesNotContain(sibling);
    }

    @Test
    void shouldUseSmallestWindowAmongPrimaryAndBackupModels() {
        ModelInputEvidenceSelector selector = selector(router(1024, 4, 600, 4));

        assertThat(selector.availableInputTokens("answer")).isEqualTo(84);
    }

    @Test
    void shouldKeepEvidenceWhenUnconfiguredModelUsesFallbackWindow() {
        ModelInputEvidenceSelector selector = selector(router(0, 0));
        RetrievalChunk directHit = chunk("child-1", null, "实时通信技术栈", "RERANK");

        List<RetrievalChunk> selected = selector.select(List.of(directHit), List.of(directHit), 3216, "answer");

        assertThat(selected).containsExactly(directHit);
    }

    @Test
    void shouldUseConfiguredFallbackWindowWhenModelDoesNotDeclareIt() {
        ModelInputEvidenceProperties properties = new ModelInputEvidenceProperties();
        properties.setFallbackContextWindowTokens(16_384);
        properties.setFallbackReservedOutputTokens(1_024);
        properties.setInputSafetyMarginTokens(512);
        ModelInputEvidenceSelector selector = new ModelInputEvidenceSelector(router(0, 0), properties);

        assertThat(selector.availableInputTokens("answer")).isEqualTo(14_848);
    }

    private ModelInputEvidenceSelector selector(ModelRouter modelRouter) {
        return new ModelInputEvidenceSelector(modelRouter, new ModelInputEvidenceProperties());
    }

    private ModelRouter router(int... limits) {
        List<ModelRouteDecision> candidates = java.util.stream.IntStream.range(0, limits.length / 2)
                .mapToObj(index -> new ModelRouteDecision("model-" + index,
                        ModelProfileProperties.builder().contextWindowTokens(limits[index * 2])
                                .reservedOutputTokens(limits[index * 2 + 1]).build(), index > 0))
                .toList();
        return new ModelRouter() {
            @Override
            public ModelRoutePlan plan(ModelRouteContext context) {
                return new ModelRoutePlan(context.routeKey(), ModelRouteStrategy.PRIMARY_BACKUP, candidates);
            }
        };
    }

    private RetrievalChunk chunk(String id, String parentId, String content, String channel) {
        return new RetrievalChunk(id, 1L, 1, parentId, "文档", "知识库", content, 0.9D, channel, 1);
    }
}
