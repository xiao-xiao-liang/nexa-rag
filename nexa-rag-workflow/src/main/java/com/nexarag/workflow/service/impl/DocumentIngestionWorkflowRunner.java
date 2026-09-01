package com.nexarag.workflow.service.impl;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.workflow.service.WorkflowGraphRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.nexarag.workflow.constants.DocumentIngestionGraphConstants.DOCUMENT_INGESTION_GRAPH_NAME;
import static com.nexarag.workflow.constants.DocumentIngestionGraphConstants.THREAD_ID_SEPARATOR;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.*;

/**
 * 文档入库工作流 Runner，负责编译并启动文档入库 Graph。
 */
@Service
public class DocumentIngestionWorkflowRunner implements WorkflowGraphRunner {

    private final CompiledGraph compiledGraph;

    public DocumentIngestionWorkflowRunner(@Qualifier("documentIngestionGraph") StateGraph documentIngestionGraph) {
        try {
            this.compiledGraph = documentIngestionGraph.compile();
        } catch (GraphStateException exception) {
            throw new ServiceException("文档入库 Graph 编译失败", exception,
                    com.nexarag.common.error.BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 返回文档入库图名称。
     *
     * @return 图名称
     */
    @Override
    public String graphName() {
        return DOCUMENT_INGESTION_GRAPH_NAME;
    }

    /**
     * 运行文档入库图。
     *
     * @param initialState 初始状态
     */
    @Override
    public void run(Map<String, Object> initialState) {
        // 1. 校验并解析文档版本处理边界
        DocumentProcessingBoundary boundary = resolveProcessingBoundary(initialState);

        // 2. 使用三元组生成 Graph threadId，隔离同一文档的不同版本和处理轮次
        String threadId = DOCUMENT_INGESTION_GRAPH_NAME + THREAD_ID_SEPARATOR + boundary.documentId()
                + THREAD_ID_SEPARATOR + boundary.documentVersionId() + THREAD_ID_SEPARATOR + boundary.processId();
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        // 3. 启动 Graph，异常交给调用方决定是否释放队列任务
        compiledGraph.stream(initialState, runnableConfig).blockLast();
    }

    private DocumentProcessingBoundary resolveProcessingBoundary(Map<String, Object> initialState) {
        Long documentId = resolvePositiveLong(initialState, DOCUMENT_ID);
        Long documentVersionId = resolvePositiveLong(initialState, DOCUMENT_VERSION_ID);
        Object rawProcessId = initialState == null ? null : initialState.get(PROCESS_ID);
        if (rawProcessId instanceof String processId && !processId.isBlank()) {
            return new DocumentProcessingBoundary(documentId, documentVersionId, processId);
        }
        throw new ServiceException("文档入库工作流缺少有效 processId");
    }

    private Long resolvePositiveLong(Map<String, Object> initialState, String stateKey) {
        Object rawValue = initialState == null ? null : initialState.get(stateKey);
        if (rawValue instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        if (rawValue instanceof String text && text.matches("[1-9]\\d*")) {
            return Long.valueOf(text);
        }
        throw new ServiceException("文档入库工作流缺少有效 " + stateKey);
    }

    /**
     * 文档入库工作流的不可分割处理边界。
     */
    private record DocumentProcessingBoundary(Long documentId, Long documentVersionId, String processId) {
    }
}
