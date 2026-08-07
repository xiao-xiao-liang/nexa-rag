package com.nexarag.workflow.node.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.service.DocumentService;
import com.nexarag.retrieval.dto.res.DocumentIndexResult;
import com.nexarag.retrieval.service.DocumentIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.CURRENT_STAGE;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.CURRENT_STATUS;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_ID;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.ROUTE_TARGET;
import static com.nexarag.workflow.util.DocumentIngestionStateUtil.requiredLong;

/**
 * 文档索引节点，负责调用检索模块完成向量索引和关键词索引写入。
 */
@Component
@RequiredArgsConstructor
public class IndexingNode implements NodeAction {

    private final DocumentService documentService;
    private final DocumentIndexService documentIndexService;

    /**
     * 执行文档索引阶段。
     *
     * @param state Graph 状态
     * @return 索引后的状态增量
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 1. 读取文档ID并调用检索模块索引服务
        Long documentId = requiredLong(state, DOCUMENT_ID);
        DocumentIndexResult indexResult = documentIndexService.indexDocument(documentId);
        if (indexResult.success()) {
            return endState(DocumentStatus.INDEXED);
        }

        // 2. 索引失败时读取文档服务写入的稳定状态
        Document document = documentService.getRequiredDocument(documentId);
        DocumentStatus status = document.getStatus();
        if (status == DocumentStatus.QUEUED) {
            throw new ServiceException("文档索引失败，等待重新入队，documentId=" + documentId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        if (status == DocumentStatus.FAILED || status == DocumentStatus.INDEXED) {
            return endState(status);
        }

        // 3. 非预期状态说明索引服务和 Graph 路由约定不一致
        throw new ServiceException("文档索引后状态异常，documentId=" + documentId + "，status=" + status,
                DocumentErrorCode.DOCUMENT_STATUS_INVALID);
    }

    private Map<String, Object> endState(DocumentStatus status) {
        return Map.of(
                CURRENT_STAGE, DocumentStatus.INDEXING.name(),
                CURRENT_STATUS, status.name(),
                ROUTE_TARGET, END
        );
    }
}
