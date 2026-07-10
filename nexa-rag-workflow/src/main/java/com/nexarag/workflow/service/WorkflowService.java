package com.nexarag.workflow.service;

import java.util.Map;

/**
 * 通用工作流服务入口，负责按照图名称启动对应业务工作流。
 */
public interface WorkflowService {

    /**
     * 运行指定工作流图。
     *
     * @param graphName    图名称
     * @param initialState 初始状态
     */
    void run(String graphName, Map<String, Object> initialState);
}
