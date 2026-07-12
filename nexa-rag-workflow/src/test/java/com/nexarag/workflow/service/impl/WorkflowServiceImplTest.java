package com.nexarag.workflow.service.impl;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.workflow.service.StreamingWorkflowGraphRunner;
import com.nexarag.workflow.service.WorkflowGraphRunner;
import com.nexarag.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 工作流服务分发实现测试，验证图名称到 Runner 的策略路由。
 */
class WorkflowServiceImplTest {

    @Test
    void runShouldDispatchToMatchedRunner() {
        RecordingRunner runner = new RecordingRunner("document-ingestion");
        WorkflowService service = new WorkflowServiceImpl(List.of(runner), List.of());

        Map<String, Object> initialState = Map.of("documentId", 1001L);
        service.run("document-ingestion", initialState);

        assertThat(runner.receivedState()).isEqualTo(initialState);
    }

    @Test
    void runShouldRejectUnknownGraphName() {
        WorkflowService service = new WorkflowServiceImpl(List.of(new RecordingRunner("document-ingestion")), List.of());

        assertThatThrownBy(() -> service.run("unknown-graph", Map.of("documentId", 1001L)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未找到工作流图");
    }

    @Test
    void constructorShouldRejectDuplicatedGraphName() {
        assertThatThrownBy(() -> new WorkflowServiceImpl(List.of(
                new RecordingRunner("document-ingestion"),
                new RecordingRunner("document-ingestion")
        ), List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("工作流图名称重复");
    }

    @Test
    void streamShouldDispatchToMatchedStreamingRunner() {
        Flux<GraphResponse<StreamingOutput<?>>> expected = Flux.empty();
        RecordingStreamingRunner runner = new RecordingStreamingRunner("chat-conversation", expected);
        WorkflowService service = new WorkflowServiceImpl(List.of(), List.of(runner));

        Flux<GraphResponse<StreamingOutput<?>>> actual = service.stream("chat-conversation", Map.of("content", "你好"));

        assertThat(actual).isSameAs(expected);
        assertThat(runner.receivedState()).isEqualTo(Map.of("content", "你好"));
    }

    @Test
    void streamShouldRejectUnknownGraphName() {
        WorkflowService service = new WorkflowServiceImpl(List.of(), List.of());

        assertThatThrownBy(() -> service.stream("unknown", Map.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未找到流式工作流图");
    }

    @Test
    void constructorShouldRejectDuplicatedStreamingGraphName() {
        assertThatThrownBy(() -> new WorkflowServiceImpl(List.of(), List.of(
                new RecordingStreamingRunner("chat-conversation", Flux.empty()),
                new RecordingStreamingRunner("chat-conversation", Flux.empty())
        )))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("流式工作流图名称重复");
    }

    /**
     * 测试用工作流 Runner，记录最近一次收到的初始状态。
     */
    private static final class RecordingRunner implements WorkflowGraphRunner {

        private final String graphName;
        private Map<String, Object> receivedState;

        private RecordingRunner(String graphName) {
            this.graphName = graphName;
        }

        @Override
        public String graphName() {
            return graphName;
        }

        @Override
        public void run(Map<String, Object> initialState) {
            receivedState = initialState;
        }

        private Map<String, Object> receivedState() {
            return receivedState;
        }
    }

    /**
     * 测试用流式工作流 Runner，记录最近一次接收的初始状态。
     */
    private static final class RecordingStreamingRunner implements StreamingWorkflowGraphRunner {

        private final String graphName;
        private final Flux<GraphResponse<StreamingOutput<?>>> responses;
        private Map<String, Object> receivedState;

        private RecordingStreamingRunner(String graphName, Flux<GraphResponse<StreamingOutput<?>>> responses) {
            this.graphName = graphName;
            this.responses = responses;
        }

        @Override
        public String graphName() {
            return graphName;
        }

        @Override
        public Flux<GraphResponse<StreamingOutput<?>>> stream(Map<String, Object> initialState) {
            receivedState = initialState;
            return responses;
        }

        private Map<String, Object> receivedState() {
            return receivedState;
        }
    }
}
