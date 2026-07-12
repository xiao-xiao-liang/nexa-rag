package com.nexarag.workflow.service;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 流式工作流图执行器，负责返回 Graph 的连续输出。
 */
public interface StreamingWorkflowGraphRunner {

    /**
     * 返回工作流图名称。
     *
     * @return 工作流图名称
     */
    String graphName();

    /**
     * 流式运行指定工作流图。
     *
     * @param initialState 初始状态
     * @return Graph 流式输出
     */
    Flux<GraphResponse<StreamingOutput<?>>> stream(Map<String, Object> initialState);
}
