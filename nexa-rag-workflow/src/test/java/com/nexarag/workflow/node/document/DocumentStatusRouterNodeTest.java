package com.nexarag.workflow.node.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.CHUNKING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.INDEXING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.PARSING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.CURRENT_STATUS;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_ID;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.ROUTE_TARGET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 文档状态路由节点测试，验证稳定状态到下一节点的映射规则。
 */
class DocumentStatusRouterNodeTest {

    @ParameterizedTest
    @MethodSource("statusRoutes")
    void applyShouldRouteByDocumentStatus(DocumentStatus status, String expectedTarget) throws Exception {
        DocumentService documentService = mock(DocumentService.class);
        when(documentService.getRequiredDocument(1001L)).thenReturn(Document.builder()
                .documentId(1001L)
                .status(status)
                .build());
        DocumentStatusRouterNode node = new DocumentStatusRouterNode(documentService);

        Map<String, Object> result = node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L)));

        assertThat(result).containsEntry(CURRENT_STATUS, status.name());
        assertThat(result).containsEntry(ROUTE_TARGET, expectedTarget);
    }

    @Test
    void applyShouldRejectMissingDocumentId() {
        DocumentStatusRouterNode node = new DocumentStatusRouterNode(mock(DocumentService.class));

        assertThatThrownBy(() -> node.apply(new OverAllState(Map.of())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(DOCUMENT_ID);
    }

    static Stream<Arguments> statusRoutes() {
        return Stream.of(
                arguments(DocumentStatus.QUEUED, PARSING_NODE),
                arguments(DocumentStatus.PARSING, PARSING_NODE),
                arguments(DocumentStatus.PARSED, CHUNKING_NODE),
                arguments(DocumentStatus.CHUNKING, CHUNKING_NODE),
                arguments(DocumentStatus.CHUNKED, INDEXING_NODE),
                arguments(DocumentStatus.INDEXING, INDEXING_NODE),
                arguments(DocumentStatus.INDEXED, END),
                arguments(DocumentStatus.FAILED, END),
                arguments(DocumentStatus.UPLOADED, END)
        );
    }
}
