package com.nexarag.workflow.node.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.*;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.*;
import static com.nexarag.workflow.util.DocumentIngestionStateUtil.requiredLong;

/**
 * 文档状态路由节点，根据数据库稳定状态决定文档入库 Graph 的起始节点。
 */
@Component
@RequiredArgsConstructor
public class DocumentStatusRouterNode implements NodeAction {

    private final DocumentVersionService documentVersionService;

    /**
     * 根据文档状态计算下一跳节点。
     *
     * @param state Graph 状态
     * @return 当前状态和路由目标
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 1. 读取文档版本边界并查询数据库稳定状态
        Long documentId = requiredLong(state, DOCUMENT_ID);
        Long documentVersionId = requiredLong(state, DOCUMENT_VERSION_ID);
        DocumentVersionDO documentVersion = documentVersionService.getRequiredVersion(documentId, documentVersionId);
        DocumentVersionStatus status = documentVersion.getStatus();

        // 2. 根据状态计算下一跳节点，不在路由阶段修改数据库
        String routeTarget = routeTarget(status);
        return Map.of(
                CURRENT_STATUS, status.name(),
                ROUTE_TARGET, routeTarget
        );
    }

    private String routeTarget(DocumentVersionStatus status) {
        return switch (status) {
            case QUEUED, PARSING -> PARSING_NODE;
            case PARSED, CHUNKING -> CHUNKING_NODE;
            case CHUNKED, INDEXING -> INDEXING_NODE;
            case UPLOADED, INDEX_READY, FAILED, DELETING -> END;
        };
    }
}
