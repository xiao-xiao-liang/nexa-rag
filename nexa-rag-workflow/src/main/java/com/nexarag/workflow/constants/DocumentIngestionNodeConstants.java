package com.nexarag.workflow.constants;

/**
 * 文档入库 Graph 节点名称常量，统一维护节点和条件边目标。
 */
public final class DocumentIngestionNodeConstants {

    public static final String STATUS_ROUTER_NODE = "statusRouterNode";

    public static final String PARSING_NODE = "parsingNode";

    public static final String CHUNKING_NODE = "chunkingNode";

    public static final String INDEXING_NODE = "indexingNode";

    private DocumentIngestionNodeConstants() {
    }
}
