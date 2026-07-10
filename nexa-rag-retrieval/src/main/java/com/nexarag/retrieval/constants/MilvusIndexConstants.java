package com.nexarag.retrieval.constants;

/**
 * Milvus 索引常量，统一维护 Milvus 集合特有字段和字段长度限制。
 */
public final class MilvusIndexConstants {

    public static final String VECTOR = "vector";
    public static final int CHUNK_ID_MAX_LENGTH = 128;
    public static final int TEXT_MAX_LENGTH = 65535;
    public static final int METADATA_MAX_LENGTH = 65535;
    public static final String DATABASE_NAME_REGEX = "^[A-Za-z0-9_]+$";

    private MilvusIndexConstants() {
    }
}
