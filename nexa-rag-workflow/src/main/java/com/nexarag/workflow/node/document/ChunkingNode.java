package com.nexarag.workflow.node.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentChunkingService;
import com.nexarag.document.service.DocumentVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.INDEXING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.*;
import static com.nexarag.workflow.util.DocumentIngestionStateUtil.requiredLong;

/**
 * 文档切分节点，负责调用文档模块完成切分阶段并计算后续路由。
 */
@Component
@RequiredArgsConstructor
public class ChunkingNode implements NodeAction {

    private final DocumentVersionService documentVersionService;
    private final DocumentChunkingService documentChunkingService;

    /**
     * 执行文档切分阶段。
     *
     * @param state Graph 状态
     * @return 切分后的状态增量
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 1. 读取文档版本边界并调用切分服务
        Long documentId = requiredLong(state, DOCUMENT_ID);
        Long documentVersionId = requiredLong(state, DOCUMENT_VERSION_ID);
        documentChunkingService.chunk(documentId, documentVersionId);

        // 2. 读取切分服务写入的版本稳定状态
        DocumentVersionDO documentVersion = documentVersionService.getRequiredVersion(documentId, documentVersionId);
        DocumentVersionStatus status = documentVersion.getStatus();
        if (status == DocumentVersionStatus.CHUNKED) {
            return Map.of(
                    CURRENT_STAGE, DocumentVersionStatus.CHUNKING.name(),
                    CURRENT_STATUS, status.name(),
                    ROUTE_TARGET, INDEXING_NODE
            );
        }
        if (status == DocumentVersionStatus.FAILED) {
            return Map.of(
                    CURRENT_STAGE, DocumentVersionStatus.CHUNKING.name(),
                    CURRENT_STATUS, status.name(),
                    ROUTE_TARGET, END
            );
        }

        // 3. 非预期状态说明阶段服务和 Graph 路由约定不一致
        throw new ServiceException("文档版本切分后状态异常，documentId=" + documentId
                + "，documentVersionId=" + documentVersionId + "，status=" + status,
                DocumentErrorCode.DOCUMENT_STATUS_INVALID);
    }
}
