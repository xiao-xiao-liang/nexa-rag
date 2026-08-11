package com.nexarag.infra.storage.service;

import com.nexarag.infra.storage.StoredFile;

import java.io.InputStream;

/**
 * 文件存储服务接口。
 */
public interface FileStorageService {

    /**
     * 保存原始文件。
     *
     * @param fileName    文件名
     * @param inputStream 文件输入流
     * @param size        文件大小
     * @return 已存储文件信息
     */
    StoredFile save(String fileName, InputStream inputStream, long size);

    /**
     * 按指定对象名保存文件。
     *
     * @param objectName  对象名
     * @param inputStream 文件输入流
     * @param size        文件大小
     * @param contentType 内容类型
     * @return 已存储文件信息
     */
    StoredFile saveAs(String objectName, InputStream inputStream, long size, String contentType);

    /**
     * 读取文件。
     *
     * @param objectName 对象名
     * @return 文件输入流
     */
    InputStream load(String objectName);

    /**
     * 解析对象的可访问地址。
     *
     * @param objectName 对象名
     * @return 对象访问地址
     */
    String resolveUrl(String objectName);

    /**
     * 删除文件。
     *
     * @param objectName 对象名
     */
    void delete(String objectName);
}
