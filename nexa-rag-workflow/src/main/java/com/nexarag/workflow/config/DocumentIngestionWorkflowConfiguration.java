package com.nexarag.workflow.config;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.nexarag.workflow.dispatcher.document.DocumentNodeDispatcher;
import com.nexarag.workflow.dispatcher.document.DocumentStatusRouterDispatcher;
import com.nexarag.workflow.node.document.ChunkingNode;
import com.nexarag.workflow.node.document.DocumentStatusRouterNode;
import com.nexarag.workflow.node.document.IndexingNode;
import com.nexarag.workflow.node.document.ParsingNode;
import com.nexarag.workflow.util.NodeBeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.nexarag.workflow.constants.DocumentIngestionGraphConstants.DOCUMENT_INGESTION_GRAPH_NAME;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.*;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.*;

/**
 * 文档入库 Workflow Graph 配置，负责注册节点、条件边和 State Key 策略。
 */
@Slf4j
@Configuration
public class DocumentIngestionWorkflowConfiguration {

    /**
     * 创建文档入库 Graph。
     *
     * @param nodeBeanUtil 节点 Bean 包装工具
     * @return 文档入库 Graph
     * @throws GraphStateException 图结构非法时抛出
     */
    @Bean("documentIngestionGraph")
    public StateGraph documentIngestionGraph(NodeBeanUtil nodeBeanUtil) throws GraphStateException {
        // 1. 定义文档入库 State Key 合并策略
        KeyStrategyFactory keyStrategyFactory = this::buildKeyStrategies;

        // 2. 注册文档入库节点
        StateGraph graph = new StateGraph(DOCUMENT_INGESTION_GRAPH_NAME, keyStrategyFactory)
                .addNode(STATUS_ROUTER_NODE, nodeBeanUtil.toAsyncNode(DocumentStatusRouterNode.class))
                .addNode(PARSING_NODE, nodeBeanUtil.toAsyncNode(ParsingNode.class))
                .addNode(CHUNKING_NODE, nodeBeanUtil.toAsyncNode(ChunkingNode.class))
                .addNode(INDEXING_NODE, nodeBeanUtil.toAsyncNode(IndexingNode.class));

        // 3. 注册起始路由和阶段间条件边
        graph.addEdge(START, STATUS_ROUTER_NODE)
                .addConditionalEdges(STATUS_ROUTER_NODE,
                        nodeBeanUtil.toAsyncEdge(DocumentStatusRouterDispatcher.class),
                        Map.of(PARSING_NODE, PARSING_NODE,
                                CHUNKING_NODE, CHUNKING_NODE,
                                INDEXING_NODE, INDEXING_NODE,
                                END, END))
                .addConditionalEdges(PARSING_NODE,
                        nodeBeanUtil.toAsyncEdge(DocumentNodeDispatcher.class),
                        Map.of(CHUNKING_NODE, CHUNKING_NODE,
                                END, END))
                .addConditionalEdges(CHUNKING_NODE,
                        nodeBeanUtil.toAsyncEdge(DocumentNodeDispatcher.class),
                        Map.of(INDEXING_NODE, INDEXING_NODE,
                                END, END))
                .addEdge(INDEXING_NODE, END);

        // 4. 输出图结构，便于开发阶段排查节点编排
        GraphRepresentation representation = graph.getGraph(GraphRepresentation.Type.PLANTUML, "document ingestion workflow");
        log.debug("文档入库 Workflow Graph 已装配，PlantUML:\n{}", representation.content());
        return graph;
    }

    private Map<String, KeyStrategy> buildKeyStrategies() {
        HashMap<String, KeyStrategy> strategies = new HashMap<>();
        strategies.put(DOCUMENT_ID, KeyStrategy.REPLACE);
        strategies.put(DOCUMENT_VERSION_ID, KeyStrategy.REPLACE);
        strategies.put(PROCESS_ID, KeyStrategy.REPLACE);
        strategies.put(CURRENT_STATUS, KeyStrategy.REPLACE);
        strategies.put(ROUTE_TARGET, KeyStrategy.REPLACE);
        strategies.put(CURRENT_STAGE, KeyStrategy.REPLACE);
        strategies.put(FAILURE_STAGE, KeyStrategy.REPLACE);
        strategies.put(FAILURE_REASON, KeyStrategy.REPLACE);
        return strategies;
    }
}
