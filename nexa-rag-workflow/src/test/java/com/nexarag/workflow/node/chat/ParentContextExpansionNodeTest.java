package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.infra.observability.langfuse.aop.LangfuseSpan;
import com.nexarag.infra.observability.langfuse.model.LangfuseObservationType;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.retriever.ParentContextExpansionRetriever;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RERANKED_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.PARENT_CONTEXT_FALLBACK_RESULTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 重排序后父子上下文扩展节点测试。 */
class ParentContextExpansionNodeTest {

    @Test
    void applyShouldReplaceRankedChunksWithExpandedContext() {
        ParentContextExpansionRetriever retriever = mock(ParentContextExpansionRetriever.class);
        RetrievalChunk rankedChunk = new RetrievalChunk("child_1", 1L, 1, "parent_1", null, null,
                "命中内容", 0.9D, "VECTOR", 1);
        RetrievalChunk parentChunk = new RetrievalChunk("parent_1", 1L, 0, null, null, null,
                "完整上下文", 0.9D, ParentContextExpansionRetriever.PARENT_CONTEXT_CHANNEL, 1);
        when(retriever.expand(List.of(rankedChunk))).thenReturn(List.of(parentChunk));
        ParentContextExpansionNode node = new ParentContextExpansionNode(retriever);

        Map<String, Object> result = node.apply(new OverAllState(Map.of(
                RERANKED_RETRIEVAL_RESULTS, List.of(rankedChunk))));

        assertThat(result.get(RERANKED_RETRIEVAL_RESULTS)).isEqualTo(List.of(parentChunk));
        assertThat(result.get(PARENT_CONTEXT_FALLBACK_RESULTS)).isEqualTo(List.of(rankedChunk));
    }

    @Test
    void applyShouldDeclareParentContextRetrieverSpan() throws NoSuchMethodException {
        LangfuseSpan span = ParentContextExpansionNode.class
                .getMethod("apply", OverAllState.class)
                .getAnnotation(LangfuseSpan.class);

        assertThat(span.name()).isEqualTo("rag.parent-context-expansion");
        assertThat(span.type()).isEqualTo(LangfuseObservationType.RETRIEVER);
        assertThat(span.attributes()).containsExactly("nexa.parent_context.input_count="
                + "#state.value('rerankedRetrievalResults', T(java.util.List).of()).size()");
        assertThat(span.resultAttributes()).containsExactly("nexa.parent_context.output_count="
                + "#result['rerankedRetrievalResults'].size()");
    }
}
