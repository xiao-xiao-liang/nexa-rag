package com.nexarag.retrieval.constants;

/**
 * 文档索引字段常量，统一维护向量索引和关键词索引共享的片段字段名称。
 */
public final class DocumentIndexFieldConstants {

    public static final String CHUNK_ID = "chunk_id";
    public static final String DOCUMENT_ID = "document_id";
    public static final String PARENT_CHUNK_ID = "parent_chunk_id";
    public static final String CHUNK_ORDER = "chunk_order";
    public static final String TEXT = "text";
    public static final String METADATA_JSON = "metadata_json";

    private DocumentIndexFieldConstants() {
    }
}
