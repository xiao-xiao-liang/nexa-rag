package com.nexarag.workflow.service.impl;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.workflow.service.WorkflowGraphRunner;
import com.nexarag.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流服务策略分发实现，按照图名称定位并运行对应 Runner。
 */
@Service
public class WorkflowServiceImpl implements WorkflowService {

    private final Map<String, WorkflowGraphRunner> runnerMap;

    public WorkflowServiceImpl(List<WorkflowGraphRunner> runners) {
        this.runnerMap = buildRunnerMap(runners);
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

    private Map<String, WorkflowGraphRunner> buildRunnerMap(List<WorkflowGraphRunner> runners) {
        Map<String, WorkflowGraphRunner> result = new HashMap<>();
        for (WorkflowGraphRunner runner : runners) {
            WorkflowGraphRunner exists = result.putIfAbsent(runner.graphName(), runner);
            if (exists != null) {
                throw new ServiceException("工作流图名称重复，graphName=" + runner.graphName());
            }
        }
        return Map.copyOf(result);
    }
}
