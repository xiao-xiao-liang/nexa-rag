package com.nexarag.workflow.config;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.nexarag.workflow.dispatcher.document.DocumentNodeDispatcher;
import com.nexarag.workflow.dispatcher.document.DocumentStatusRouterDispatcher;
import com.nexarag.workflow.node.document.ChunkingNode;
import com.nexarag.workflow.node.document.DocumentStatusRouterNode;
import com.nexarag.workflow.node.document.IndexingNode;
import com.nexarag.workflow.node.document.ParsingNode;
import com.nexarag.workflow.util.NodeBeanUtil;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 文档入库 Graph 配置测试，验证图结构可以完成编译。
 */
class DocumentIngestionWorkflowConfigurationTest {

    @Test
    void documentIngestionGraphShouldCompile() throws Exception {
        NodeBeanUtil nodeBeanUtil = mock(NodeBeanUtil.class);
        when(nodeBeanUtil.toAsyncNode(DocumentStatusRouterNode.class))
                .thenReturn(AsyncNodeAction.node_async(state -> Map.of()));
        when(nodeBeanUtil.toAsyncNode(ParsingNode.class))
                .thenReturn(AsyncNodeAction.node_async(state -> Map.of()));
        when(nodeBeanUtil.toAsyncNode(ChunkingNode.class))
                .thenReturn(AsyncNodeAction.node_async(state -> Map.of()));
        when(nodeBeanUtil.toAsyncNode(IndexingNode.class))
                .thenReturn(AsyncNodeAction.node_async(state -> Map.of()));
        when(nodeBeanUtil.toAsyncEdge(DocumentStatusRouterDispatcher.class))
                .thenReturn(AsyncEdgeAction.edge_async(state -> END));
        when(nodeBeanUtil.toAsyncEdge(DocumentNodeDispatcher.class))
                .thenReturn(AsyncEdgeAction.edge_async(state -> END));

        StateGraph graph = new DocumentIngestionWorkflowConfiguration().documentIngestionGraph(nodeBeanUtil);

        assertThatCode(graph::compile).doesNotThrowAnyException();
    }
}
