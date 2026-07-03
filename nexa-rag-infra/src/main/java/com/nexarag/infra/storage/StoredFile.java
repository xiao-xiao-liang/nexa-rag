package com.nexarag.infra.storage;

/**
 * 已存储文件信息。
 *
 * @param objectName 对象名称
 * @param url        文件访问地址
 * @param size       文件大小
 */
public record StoredFile(String objectName, String url, long size) {
}
