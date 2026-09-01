package com.nexarag.workflow.service.impl;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.nexarag.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档入库工作流 Runner 测试，验证入参校验和最小 Graph 运行能力。
 */
class DocumentIngestionWorkflowRunnerTest {

    @Test
    void runShouldRejectMissingDocumentProcessingBoundary() throws Exception {
        DocumentIngestionWorkflowRunner runner = new DocumentIngestionWorkflowRunner(buildNoopGraph());

        assertThatThrownBy(() -> runner.run(Map.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("documentId");
        assertThatThrownBy(() -> runner.run(Map.of("documentId", 1001L)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("documentVersionId");
    }

    @Test
    void runShouldAcceptDocumentProcessingBoundary() throws Exception {
        DocumentIngestionWorkflowRunner runner = new DocumentIngestionWorkflowRunner(buildNoopGraph());

        assertThatCode(() -> runner.run(Map.of("documentId", 1001L, "documentVersionId", 2001L,
                        "processId", "process-1")))
                .doesNotThrowAnyException();
    }

    private StateGraph buildNoopGraph() throws GraphStateException {
        return new StateGraph("document-ingestion", () -> Map.of())
                .addNode("noop", AsyncNodeAction.node_async(state -> Map.of()))
                .addEdge(START, "noop")
                .addEdge("noop", END);
    }
}
