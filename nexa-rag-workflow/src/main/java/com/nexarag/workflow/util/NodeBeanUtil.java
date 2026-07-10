package com.nexarag.workflow.util;

import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Graph 节点 Bean 工具类，负责从 Spring 容器获取节点并包装为异步动作。
 */
@Component
@RequiredArgsConstructor
public class NodeBeanUtil {

    private final ApplicationContext applicationContext;

    /**
     * 获取节点 Bean 并包装为 Graph 异步节点。
     *
     * @param clazz 节点类型
     * @param <T>   节点泛型
     * @return 异步节点动作
     */
    public <T extends NodeAction> AsyncNodeAction toAsyncNode(Class<T> clazz) {
        // 1. 从 Spring 容器获取节点 Bean
        NodeAction nodeAction = applicationContext.getBean(clazz);

        // 2. 包装为 StateGraph 可识别的异步节点
        return AsyncNodeAction.node_async(nodeAction);
    }

    /**
     * 获取路由 Bean 并包装为 Graph 异步边。
     *
     * @param clazz 路由类型
     * @param <T>   路由泛型
     * @return 异步边动作
     */
    public <T extends EdgeAction> AsyncEdgeAction toAsyncEdge(Class<T> clazz) {
        // 1. 从 Spring 容器获取路由 Bean
        EdgeAction edgeAction = applicationContext.getBean(clazz);

        // 2. 包装为 StateGraph 可识别的异步边
        return AsyncEdgeAction.edge_async(edgeAction);
    }
}
