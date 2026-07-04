package com.nexarag.infra.enums;

/**
 * 文件存储类型。
 */
public enum StorageType {

    /**
     * MinIO 对象存储。
     */
    MINIO,

    /**
     * RustFS 对象存储。
     */
    RUSTFS,

    /**
     * 阿里云 OSS 对象存储。
     */
    OSS,

    /**
     * 七牛云对象存储。
     */
    QINIU,

    /**
     * 本地文件存储。
     */
    LOCAL
}
