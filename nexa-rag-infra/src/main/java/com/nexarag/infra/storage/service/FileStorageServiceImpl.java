package com.nexarag.infra.storage.service;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.StorageProperties;
import com.nexarag.infra.enums.StorageType;
import com.nexarag.infra.storage.FileStorageStrategy;
import com.nexarag.infra.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

/**
 * 委派文件存储服务，根据配置的存储类型选择对应策略执行文件操作。
 */
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final StorageProperties storageProperties;
    private final List<FileStorageStrategy> storageStrategies;

    /**
     * 保存原始文件。
     *
     * @param fileName    文件名
     * @param inputStream 文件输入流
     * @param size        文件大小
     * @return 已存储文件信息
     */
    @Override
    public StoredFile save(String fileName, InputStream inputStream, long size) {
        // 1. 根据配置选择具体存储策略
        FileStorageStrategy storageStrategy = getRequiredStrategy();

        // 2. 委派给具体策略保存原始文件
        return storageStrategy.save(fileName, inputStream, size);
    }

    /**
     * 按指定对象名保存文件。
     *
     * @param objectName  对象名
     * @param inputStream 文件输入流
     * @param size        文件大小
     * @param contentType 内容类型
     * @return 已存储文件信息
     */
    @Override
    public StoredFile saveAs(String objectName, InputStream inputStream, long size, String contentType) {
        // 1. 根据配置选择具体存储策略
        FileStorageStrategy storageStrategy = getRequiredStrategy();

        // 2. 委派给具体策略按指定对象名保存文件
        return storageStrategy.saveAs(objectName, inputStream, size, contentType);
    }

    /**
     * 读取文件。
     *
     * @param objectName 对象名
     * @return 文件输入流
     */
    @Override
    public InputStream load(String objectName) {
        // 1. 根据配置选择具体存储策略
        FileStorageStrategy storageStrategy = getRequiredStrategy();

        // 2. 委派给具体策略读取文件
        return storageStrategy.load(objectName);
    }

    /**
     * 解析对象的可访问地址。
     *
     * @param objectName 对象名
     * @return 对象访问地址
     */
    @Override
    public String resolveUrl(String objectName) {
        // 1. 根据配置选择具体存储策略
        FileStorageStrategy storageStrategy = getRequiredStrategy();

        // 2. 委派给具体策略解析对象访问地址
        return storageStrategy.resolveUrl(objectName);
    }

    /**
     * 删除文件。
     *
     * @param objectName 对象名
     */
    @Override
    public void delete(String objectName) {
        // 1. 根据配置选择具体存储策略
        FileStorageStrategy storageStrategy = getRequiredStrategy();

        // 2. 委派给具体策略删除文件
        storageStrategy.delete(objectName);
    }

    private FileStorageStrategy getRequiredStrategy() {
        StorageType storageType = storageProperties.getType();
        return storageStrategies.stream()
                .filter(storageStrategy -> storageStrategy.storageType() == storageType)
                .findFirst()
                .orElseThrow(() -> new ServiceException("未找到文件存储策略，storageType=" + storageType));
    }
}
