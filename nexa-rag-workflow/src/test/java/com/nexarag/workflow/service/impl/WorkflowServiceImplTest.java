package com.nexarag.workflow.service.impl;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.workflow.service.WorkflowGraphRunner;
import com.nexarag.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;

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
        WorkflowService service = new WorkflowServiceImpl(List.of(runner));

        Map<String, Object> initialState = Map.of("documentId", 1001L);
        service.run("document-ingestion", initialState);

        assertThat(runner.receivedState()).isEqualTo(initialState);
    }

    @Test
    void runShouldRejectUnknownGraphName() {
        WorkflowService service = new WorkflowServiceImpl(List.of(new RecordingRunner("document-ingestion")));

        assertThatThrownBy(() -> service.run("unknown-graph", Map.of("documentId", 1001L)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未找到工作流图");
    }

    @Test
    void constructorShouldRejectDuplicatedGraphName() {
        assertThatThrownBy(() -> new WorkflowServiceImpl(List.of(
                new RecordingRunner("document-ingestion"),
                new RecordingRunner("document-ingestion")
        )))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("工作流图名称重复");
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
}
