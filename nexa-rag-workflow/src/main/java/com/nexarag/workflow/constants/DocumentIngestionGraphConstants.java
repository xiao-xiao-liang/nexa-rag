package com.nexarag.workflow.constants;

/**
 * 文档入库 Graph 常量，统一维护图名称和线程标识规则。
 */
public final class DocumentIngestionGraphConstants {

    public static final String DOCUMENT_INGESTION_GRAPH_NAME = "document-ingestion";

    public static final String THREAD_ID_SEPARATOR = ":";

    public static final String PARSE_FAILURE_REASON = "文档解析失败";

    private DocumentIngestionGraphConstants() {
    }
}
