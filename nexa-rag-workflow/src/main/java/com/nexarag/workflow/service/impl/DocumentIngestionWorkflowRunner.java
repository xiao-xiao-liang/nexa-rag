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
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_ID;

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
        // 1. 校验并解析文档ID
        Long documentId = resolveDocumentId(initialState);

        // 2. 使用稳定规则生成 Graph threadId
        String threadId = DOCUMENT_INGESTION_GRAPH_NAME + THREAD_ID_SEPARATOR + documentId;
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        // 3. 启动 Graph，异常交给调用方决定是否释放队列任务
        compiledGraph.stream(initialState, runnableConfig).blockLast();
    }

    private Long resolveDocumentId(Map<String, Object> initialState) {
        Object rawDocumentId = initialState == null ? null : initialState.get(DOCUMENT_ID);
        if (rawDocumentId instanceof Number number) {
            return number.longValue();
        }
        if (rawDocumentId instanceof String text && text.matches("\\d+")) {
            return Long.valueOf(text);
        }
        throw new ServiceException("文档入库工作流缺少有效 documentId");
    }
}
