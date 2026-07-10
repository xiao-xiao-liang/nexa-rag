package com.nexarag.workflow.dispatcher.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.nexarag.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.CHUNKING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.INDEXING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.PARSING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.ROUTE_TARGET;
import static com.nexarag.workflow.util.DocumentIngestionStateUtil.requiredString;

/**
 * 文档状态路由 Dispatcher，将路由节点输出转换为 Graph 条件边目标。
 */
@Component
public class DocumentStatusRouterDispatcher implements EdgeAction {

    /**
     * 读取路由目标并返回下一节点。
     *
     * @param state Graph 状态
     * @return 下一节点名称
     */
    @Override
    public String apply(OverAllState state) {
        // 1. 读取路由节点计算出的目标
        String routeTarget = requiredString(state, ROUTE_TARGET);

        // 2. 校验目标只能进入文档入库允许的起始节点
        return switch (routeTarget) {
            case PARSING_NODE, CHUNKING_NODE, INDEXING_NODE, END -> routeTarget;
            default -> throw new ServiceException("未知文档入库路由，routeTarget=" + routeTarget);
        };
    }
}
