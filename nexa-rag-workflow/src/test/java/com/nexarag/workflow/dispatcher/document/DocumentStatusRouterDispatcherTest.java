package com.nexarag.workflow.dispatcher.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.CHUNKING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.INDEXING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.PARSING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.ROUTE_TARGET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档状态路由 Dispatcher 测试，验证初始路由目标转换规则。
 */
class DocumentStatusRouterDispatcherTest {

    @ParameterizedTest
    @ValueSource(strings = {PARSING_NODE, CHUNKING_NODE, INDEXING_NODE, END})
    void applyShouldReturnAllowedRouteTarget(String routeTarget) throws Exception {
        DocumentStatusRouterDispatcher dispatcher = new DocumentStatusRouterDispatcher();

        String next = dispatcher.apply(new OverAllState(Map.of(ROUTE_TARGET, routeTarget)));

        assertThat(next).isEqualTo(routeTarget);
    }

    @Test
    void applyShouldRejectUnknownRouteTarget() {
        DocumentStatusRouterDispatcher dispatcher = new DocumentStatusRouterDispatcher();

        assertThatThrownBy(() -> dispatcher.apply(new OverAllState(Map.of(ROUTE_TARGET, "bad-node"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未知文档入库路由");
    }
}
