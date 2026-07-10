package com.nexarag.workflow.dispatcher.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.nexarag.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.CHUNKING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.INDEXING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.ROUTE_TARGET;
import static com.nexarag.workflow.util.DocumentIngestionStateUtil.requiredString;

/**
 * 文档处理节点 Dispatcher，用于解析和切分节点之后的条件路由。
 */
@Component
public class DocumentNodeDispatcher implements EdgeAction {

    /**
     * 读取节点输出的路由目标。
     *
     * @param state Graph 状态
     * @return 下一节点名称
     */
    @Override
    public String apply(OverAllState state) {
        // 1. 读取当前节点输出的路由目标
        String routeTarget = requiredString(state, ROUTE_TARGET);

        // 2. 普通节点只允许向后流转或结束
        return switch (routeTarget) {
            case CHUNKING_NODE, INDEXING_NODE, END -> routeTarget;
            default -> throw new ServiceException("未知文档入库路由，routeTarget=" + routeTarget);
        };
    }
}
