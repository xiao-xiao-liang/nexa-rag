package com.nexarag.workflow.config;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.nexarag.workflow.dispatcher.chat.RetrievalFusionDispatcher;
import com.nexarag.workflow.node.chat.AnswerGenerationNode;
import com.nexarag.workflow.node.chat.AssistantMessagePersistenceNode;
import com.nexarag.workflow.node.chat.ConversationContextNode;
import com.nexarag.workflow.node.chat.ConversationValidationNode;
import com.nexarag.workflow.node.chat.IntentRecognitionNode;
import com.nexarag.workflow.node.chat.QuestionRewriteNode;
import com.nexarag.workflow.node.chat.RerankNode;
import com.nexarag.workflow.node.chat.EvidenceQualityNode;
import com.nexarag.workflow.node.chat.RetrievalFusionNode;
import com.nexarag.workflow.node.chat.RetrievalNode;
import com.nexarag.workflow.node.chat.SectionExpansionNode;
import com.nexarag.workflow.util.NodeBeanUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.nexarag.workflow.constants.ChatWorkflowGraphConstants.CHAT_CONVERSATION_GRAPH_NAME;
import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.*;

/**
 * Chat Workflow Graph 配置，负责注册会话对话节点和检索条件边。
 */
@Configuration
@ConditionalOnProperty(prefix = "nexa.chat", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChatWorkflowConfiguration {

    @Bean("chatConversationGraph")
    public StateGraph chatConversationGraph(NodeBeanUtil nodeBeanUtil) throws GraphStateException {
        StateGraph graph = new StateGraph(CHAT_CONVERSATION_GRAPH_NAME, this::strategies)
                .addNode(CONVERSATION_VALIDATION_NODE, nodeBeanUtil.toAsyncNode(ConversationValidationNode.class))
                .addNode(CONVERSATION_CONTEXT_NODE, nodeBeanUtil.toAsyncNode(ConversationContextNode.class))
                .addNode(QUESTION_REWRITE_NODE, nodeBeanUtil.toAsyncNode(QuestionRewriteNode.class))
                .addNode(INTENT_RECOGNITION_NODE, nodeBeanUtil.toAsyncNode(IntentRecognitionNode.class))
                .addNode(RETRIEVAL_NODE, nodeBeanUtil.toAsyncNode(RetrievalNode.class))
                .addNode(RETRIEVAL_FUSION_NODE, nodeBeanUtil.toAsyncNode(RetrievalFusionNode.class))
                .addNode(SECTION_EXPANSION_NODE, nodeBeanUtil.toAsyncNode(SectionExpansionNode.class))
                .addNode(RERANK_NODE, nodeBeanUtil.toAsyncNode(RerankNode.class))
                .addNode(EVIDENCE_QUALITY_NODE, nodeBeanUtil.toAsyncNode(EvidenceQualityNode.class))
                .addNode(ANSWER_GENERATION_NODE, nodeBeanUtil.toAsyncNode(AnswerGenerationNode.class))
                .addNode(ASSISTANT_MESSAGE_PERSISTENCE_NODE,
                        nodeBeanUtil.toAsyncNode(AssistantMessagePersistenceNode.class));
        graph.addEdge(START, CONVERSATION_VALIDATION_NODE)
                .addEdge(CONVERSATION_VALIDATION_NODE, CONVERSATION_CONTEXT_NODE)
                .addEdge(CONVERSATION_CONTEXT_NODE, QUESTION_REWRITE_NODE)
                .addEdge(QUESTION_REWRITE_NODE, INTENT_RECOGNITION_NODE)
                .addEdge(INTENT_RECOGNITION_NODE, RETRIEVAL_NODE)
                .addEdge(RETRIEVAL_NODE, RETRIEVAL_FUSION_NODE)
                .addConditionalEdges(RETRIEVAL_FUSION_NODE,
                        nodeBeanUtil.toAsyncEdge(RetrievalFusionDispatcher.class),
                        Map.of(SECTION_EXPANSION_NODE, SECTION_EXPANSION_NODE, RERANK_NODE, RERANK_NODE))
                .addEdge(SECTION_EXPANSION_NODE, RERANK_NODE)
                .addEdge(RERANK_NODE, EVIDENCE_QUALITY_NODE)
                .addEdge(EVIDENCE_QUALITY_NODE, ANSWER_GENERATION_NODE)
                .addEdge(ANSWER_GENERATION_NODE, ASSISTANT_MESSAGE_PERSISTENCE_NODE)
                .addEdge(ASSISTANT_MESSAGE_PERSISTENCE_NODE, END);
        return graph;
    }

    private Map<String, KeyStrategy> strategies() {
        Map<String, KeyStrategy> strategies = new HashMap<>();
        for (java.lang.reflect.Field field : com.nexarag.workflow.constants.ChatWorkflowStateKeys.class.getFields()) {
            try {
                strategies.put((String) field.get(null), KeyStrategy.REPLACE);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("读取 Chat Workflow 状态键失败", exception);
            }
        }
        return strategies;
    }
}
