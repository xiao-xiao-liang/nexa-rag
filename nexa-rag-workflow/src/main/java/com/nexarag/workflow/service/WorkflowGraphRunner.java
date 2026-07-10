package com.nexarag.workflow.service;

import java.util.Map;

/**
 * 工作流图运行策略接口，每个业务图提供一个独立 Runner。
 */
public interface WorkflowGraphRunner {

    /**
     * 返回当前 Runner 支持的图名称。
     *
     * @return 图名称
     */
    String graphName();

    /**
     * 运行当前业务图。
     *
     * @param initialState 初始状态
     */
    void run(Map<String, Object> initialState);
}
