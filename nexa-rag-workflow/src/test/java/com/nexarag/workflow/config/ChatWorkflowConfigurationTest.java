package com.nexarag.workflow.config;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.nexarag.workflow.util.NodeBeanUtil;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.ANSWER_GENERATION_NODE;
import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.ASSISTANT_MESSAGE_PERSISTENCE_NODE;
import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.RETRIEVAL_FUSION_NODE;
import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.RETRIEVAL_NODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Chat Workflow 图配置测试，验证主链路和检索回环节点均已注册。
 */
class ChatWorkflowConfigurationTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void chatConversationGraphShouldContainMainChainAndRetrievalLoop() throws Exception {
        NodeBeanUtil nodeBeanUtil = mock(NodeBeanUtil.class);
        AsyncNodeAction nodeAction = state -> CompletableFuture.completedFuture(Map.of());
        AsyncEdgeAction edgeAction = state -> CompletableFuture.completedFuture(RETRIEVAL_NODE);
        when(nodeBeanUtil.toAsyncNode(any(Class.class))).thenReturn(nodeAction);
        when(nodeBeanUtil.toAsyncEdge(any(Class.class))).thenReturn(edgeAction);

        var graph = new ChatWorkflowConfiguration().chatConversationGraph(nodeBeanUtil);
        String representation = graph.getGraph(GraphRepresentation.Type.PLANTUML, "chat").content();

        assertThat(representation).contains(RETRIEVAL_NODE, RETRIEVAL_FUSION_NODE,
                ANSWER_GENERATION_NODE, ASSISTANT_MESSAGE_PERSISTENCE_NODE);
        graph.compile();
    }
}
