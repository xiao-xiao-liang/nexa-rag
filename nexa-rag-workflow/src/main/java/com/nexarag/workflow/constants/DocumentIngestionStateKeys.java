package com.nexarag.workflow.constants;

/**
 * 文档入库 Graph State Key 常量，避免节点之间使用散落字符串。
 */
public final class DocumentIngestionStateKeys {

    public static final String DOCUMENT_ID = "documentId";

    public static final String CURRENT_STATUS = "currentStatus";

    public static final String ROUTE_TARGET = "routeTarget";

    public static final String CURRENT_STAGE = "currentStage";

    public static final String FAILURE_STAGE = "failureStage";

    public static final String FAILURE_REASON = "failureReason";

    private DocumentIngestionStateKeys() {
    }
}
