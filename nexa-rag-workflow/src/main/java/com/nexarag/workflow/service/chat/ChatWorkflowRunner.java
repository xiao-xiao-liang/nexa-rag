package com.nexarag.workflow.service.chat;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.workflow.service.StreamingWorkflowGraphRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowGraphConstants.CHAT_CONVERSATION_GRAPH_NAME;
import static com.nexarag.workflow.constants.ChatWorkflowGraphConstants.CHAT_THREAD_PREFIX;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;

/**
 * Chat Workflow Runner，负责编译并以请求级线程标识运行对话 Graph。
 */
@Service
@ConditionalOnBean(name = "chatConversationGraph")
public class ChatWorkflowRunner implements StreamingWorkflowGraphRunner {
    private final CompiledGraph compiledGraph;

    public ChatWorkflowRunner(@Qualifier("chatConversationGraph") StateGraph graph) {
        try {
            this.compiledGraph = graph.compile();
        } catch (GraphStateException exception) {
            throw new ServiceException("Chat Workflow Graph 编译失败");
        }
    }

    @Override
    public String graphName() {
        return CHAT_CONVERSATION_GRAPH_NAME;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Flux<GraphResponse<StreamingOutput<?>>> stream(Map<String, Object> initialState) {
        String traceId = String.valueOf(initialState.get(TRACE_ID));
        RunnableConfig config = RunnableConfig.builder().threadId(CHAT_THREAD_PREFIX + traceId).build();
        return compiledGraph.graphResponseStream(initialState, config)
                .map(response -> (GraphResponse<StreamingOutput<?>>) (GraphResponse) response);
    }
}
