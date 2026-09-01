package com.nexarag.workflow.node.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.retrieval.dto.res.DocumentIndexResult;
import com.nexarag.retrieval.service.DocumentIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.*;
import static com.nexarag.workflow.util.DocumentIngestionStateUtil.requiredLong;
import static com.nexarag.workflow.util.DocumentIngestionStateUtil.requiredString;

/**
 * 文档索引节点，负责调用检索模块完成向量索引和关键词索引写入。
 */
@Component
@RequiredArgsConstructor
public class IndexingNode implements NodeAction {

    private final DocumentService documentService;
    private final DocumentVersionService documentVersionService;
    private final DocumentIndexService documentIndexService;

    /**
     * 执行文档索引阶段。
     *
     * @param state Graph 状态
     * @return 索引后的状态增量
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 1. 读取三元处理边界并调用检索模块版本索引服务
        Long documentId = requiredLong(state, DOCUMENT_ID);
        Long documentVersionId = requiredLong(state, DOCUMENT_VERSION_ID);
        String processId = requiredString(state, PROCESS_ID);
        DocumentIndexResult indexResult = documentIndexService.indexDocument(documentId, documentVersionId);
        if (indexResult.success()) {
            return endState(DocumentVersionStatus.INDEX_READY);
        }

        // 2. 索引失败时读取当前版本稳定状态，并拒绝已失效处理轮次
        DocumentVersionDO documentVersion = documentVersionService.getRequiredVersion(documentId, documentVersionId);
        if (!processId.equals(documentVersion.getProcessId())) {
            throw new ServiceException("文档索引处理轮次已失效，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        DocumentVersionStatus status = documentVersion.getStatus();
        if (status == DocumentVersionStatus.QUEUED) {
            throw new ServiceException("文档版本索引失败，等待重新入队，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        if (status == DocumentVersionStatus.FAILED || status == DocumentVersionStatus.INDEX_READY) {
            return endState(status);
        }

        // 3. 非预期状态说明索引服务和 Graph 路由约定不一致
        throw new ServiceException("文档版本索引后状态异常，documentId=" + documentId
                + "，documentVersionId=" + documentVersionId + "，status=" + status,
                DocumentErrorCode.DOCUMENT_STATUS_INVALID);
    }

    private Map<String, Object> endState(DocumentVersionStatus status) {
        return Map.of(
                CURRENT_STAGE, DocumentVersionStatus.INDEXING.name(),
                CURRENT_STATUS, status.name(),
                ROUTE_TARGET, END
        );
    }
}
