package com.nexarag.workflow.service.chat;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import com.nexarag.model.toolkits.prompt.PromptReleaseResolver;
import com.nexarag.workflow.request.ChatWorkflowRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.PROMPT_EXECUTION_SNAPSHOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chat Workflow Runner 测试，验证初始状态和请求级线程标识。
 */
class ChatWorkflowRunnerTest {

    @Test
    void streamShouldUseTraceIdAsUniqueGraphThread() throws Exception {
        StateGraph graph = mock(StateGraph.class);
        CompiledGraph compiledGraph = mock(CompiledGraph.class);
        when(graph.compile()).thenReturn(compiledGraph);
        when(compiledGraph.graphResponseStream(any(Map.class), any(RunnableConfig.class))).thenReturn(Flux.empty());
        PromptReleaseResolver resolver = mock(PromptReleaseResolver.class);
        PromptExecutionSnapshot snapshot = PromptExecutionSnapshot.of(Map.of());
        when(resolver.resolve(any(), org.mockito.ArgumentMatchers.eq("u1"))).thenReturn(snapshot);
        ChatWorkflowRunner runner = new ChatWorkflowRunner(graph, resolver);
        ChatWorkflowRequest request = new ChatWorkflowRequest("u1", null, "你好", "g1", "t1");

        StepVerifier.create(runner.stream(request.toInitialState())).verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> stateCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<RunnableConfig> configCaptor = ArgumentCaptor.forClass(RunnableConfig.class);
        verify(compiledGraph).graphResponseStream(stateCaptor.capture(), configCaptor.capture());
        assertThat(stateCaptor.getValue()).containsEntry(USER_QUESTION, "你好").containsEntry(TRACE_ID, "t1");
        assertThat(stateCaptor.getValue().get(PROMPT_EXECUTION_SNAPSHOT)).isSameAs(snapshot);
        assertThat(configCaptor.getValue().threadId()).contains("chat:t1");
    }

}
