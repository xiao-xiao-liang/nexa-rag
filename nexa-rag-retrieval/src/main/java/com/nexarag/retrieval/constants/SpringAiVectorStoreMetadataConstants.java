package com.nexarag.retrieval.constants;

/**
 * Spring AI 文档元数据字段常量，维护向量检索结果恢复业务片段所需的字段名。
 */
public final class SpringAiVectorStoreMetadataConstants {

    public static final String DOCUMENT_ID = "documentId";
    public static final String DOCUMENT_VERSION_ID = "documentVersionId";
    public static final String PARENT_CHUNK_ID = "parentChunkId";
    public static final String CHUNK_ORDER = "chunkOrder";
    public static final String SECTION_ID = "sectionId";
    public static final String TEXT = "text";
    public static final String METADATA_JSON = "metadataJson";

    private SpringAiVectorStoreMetadataConstants() {
    }
}
