package com.nexarag.infra.storage;

import com.nexarag.infra.enums.StorageType;

import java.io.InputStream;

/**
 * 文件存储策略接口，定义具体存储实现需要支持的统一能力。
 */
public interface FileStorageStrategy {

    /**
     * 返回当前策略支持的存储类型。
     *
     * @return 存储类型
     */
    StorageType storageType();

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

    /**
     * 删除指定对象前缀下的全部文件。
     *
     * @param objectPrefix 已校验的对象前缀
     */
    void deleteByPrefix(String objectPrefix);
}
