package com.nexarag.workflow.node.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.document.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.CHUNKING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.INDEXING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.PARSING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.CURRENT_STATUS;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_ID;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.ROUTE_TARGET;
import static com.nexarag.workflow.util.DocumentIngestionStateUtil.requiredLong;

/**
 * 文档状态路由节点，根据数据库稳定状态决定文档入库 Graph 的起始节点。
 */
@Component
@RequiredArgsConstructor
public class DocumentStatusRouterNode implements NodeAction {

    private final DocumentService documentService;

    /**
     * 根据文档状态计算下一跳节点。
     *
     * @param state Graph 状态
     * @return 当前状态和路由目标
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 1. 读取文档ID并查询数据库稳定状态
        Long documentId = requiredLong(state, DOCUMENT_ID);
        Document document = documentService.getRequiredDocument(documentId);
        DocumentStatus status = document.getStatus();

        // 2. 根据状态计算下一跳节点，不在路由阶段修改数据库
        String routeTarget = routeTarget(status);
        return Map.of(
                CURRENT_STATUS, status.name(),
                ROUTE_TARGET, routeTarget
        );
    }

    private String routeTarget(DocumentStatus status) {
        return switch (status) {
            case QUEUED, PARSING -> PARSING_NODE;
            case PARSED, CHUNKING -> CHUNKING_NODE;
            case CHUNKED, INDEXING -> INDEXING_NODE;
            case UPLOADED, INDEXED, FAILED -> END;
        };
    }
}
