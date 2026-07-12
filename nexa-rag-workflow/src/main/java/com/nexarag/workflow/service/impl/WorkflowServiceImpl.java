package com.nexarag.workflow.service.impl;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.workflow.service.StreamingWorkflowGraphRunner;
import com.nexarag.workflow.service.WorkflowGraphRunner;
import com.nexarag.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流服务策略分发实现，按照图名称定位并运行对应 Runner。
 */
@Service
public class WorkflowServiceImpl implements WorkflowService {

    private final Map<String, WorkflowGraphRunner> runnerMap;
    private final Map<String, StreamingWorkflowGraphRunner> streamingRunnerMap;

    public WorkflowServiceImpl(List<WorkflowGraphRunner> runners,
                               List<StreamingWorkflowGraphRunner> streamingRunners) {
        this.runnerMap = buildTaskRunnerMap(runners);
        this.streamingRunnerMap = buildStreamingRunnerMap(streamingRunners);
    }

    /**
     * 运行指定工作流图。
     *
     * @param graphName    图名称
     * @param initialState 初始状态
     */
    @Override
    public void run(String graphName, Map<String, Object> initialState) {
        // 1. 根据图名称查找对应 Runner
        WorkflowGraphRunner runner = runnerMap.get(graphName);
        if (runner == null) {
            throw new ServiceException("未找到工作流图，graphName=" + graphName);
        }

        // 2. 将初始状态交给业务 Runner 执行
        runner.run(initialState);
    }

    /**
     * 流式运行指定工作流图。
     *
     * @param graphName    图名称
     * @param initialState 初始状态
     * @return Graph 流式输出
     */
    @Override
    public Flux<GraphResponse<StreamingOutput<?>>> stream(String graphName, Map<String, Object> initialState) {
        // 1. 根据图名称查找对应流式 Runner
        StreamingWorkflowGraphRunner runner = streamingRunnerMap.get(graphName);
        if (runner == null) {
            throw new ServiceException("未找到流式工作流图，graphName=" + graphName);
        }

        // 2. 将初始状态交给业务 Runner 持续输出
        return runner.stream(initialState);
    }

    private Map<String, WorkflowGraphRunner> buildTaskRunnerMap(List<WorkflowGraphRunner> runners) {
        Map<String, WorkflowGraphRunner> result = new HashMap<>();
        for (WorkflowGraphRunner runner : runners) {
            WorkflowGraphRunner exists = result.putIfAbsent(runner.graphName(), runner);
            if (exists != null) {
                throw new ServiceException("工作流图名称重复，graphName=" + runner.graphName());
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, StreamingWorkflowGraphRunner> buildStreamingRunnerMap(
            List<StreamingWorkflowGraphRunner> runners) {
        Map<String, StreamingWorkflowGraphRunner> result = new HashMap<>();
        for (StreamingWorkflowGraphRunner runner : runners) {
            StreamingWorkflowGraphRunner exists = result.putIfAbsent(runner.graphName(), runner);
            if (exists != null) {
                throw new ServiceException("流式工作流图名称重复，graphName=" + runner.graphName());
            }
        }
        return Map.copyOf(result);
    }
}
