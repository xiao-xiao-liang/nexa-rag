package com.nexarag.infra.storage.minio;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.enums.StorageType;
import com.nexarag.infra.storage.FileStorageStrategy;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.config.StorageProperties;
import com.nexarag.infra.storage.StoredFile;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;

/**
 * MinIO 文件存储策略，负责通过 MinIO 协议保存、读取和删除文档对象。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "nexa.storage", name = "type", havingValue = "MINIO", matchIfMissing = true)
@RequiredArgsConstructor
public class MinioFileStorageStrategy implements FileStorageStrategy {

    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    private static final long UNKNOWN_PART_SIZE = -1L;

    private final StorageProperties properties;
    private final ObjectNameResolver objectNameResolver;
    private volatile MinioClient minioClient;

    /**
     * 返回当前策略支持的存储类型。
     *
     * @return 存储类型
     */
    @Override
    public StorageType storageType() {
        return StorageType.MINIO;
    }

    /**
     * 保存文件到 MinIO。
     *
     * @param fileName    文件名
     * @param inputStream 文件输入流
     * @param size        文件大小
     * @return 已存储文件信息
     */
    @Override
    public StoredFile save(String fileName, InputStream inputStream, long size) {
        // 1. 校验上传文件基础参数
        validateSaveArguments(fileName, inputStream, size);

        // 2. 生成原始文件对象名并确保存储桶可用
        String objectName = objectNameResolver.resolveOriginalObjectName(fileName);
        MinioClient client = getMinioClient();
        ensureBucketExists(client);

        try {
            // 3. 上传文件流到 MinIO
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .stream(inputStream, size, UNKNOWN_PART_SIZE)
                    .contentType(CONTENT_TYPE_OCTET_STREAM)
                    .build());
            log.info("文件保存到 MinIO 成功，bucket={}，objectName={}，size={}",
                    properties.getBucket(), objectName, size);
            return new StoredFile(objectName, buildObjectUrl(objectName), size);
        } catch (Exception exception) {
            throw new ServiceException("文件保存到 MinIO 失败，objectName=" + objectName,
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 从 MinIO 读取文件。
     *
     * @param objectName 对象名称
     * @return 文件输入流
     */
    @Override
    public InputStream load(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            throw new ServiceException("MinIO 对象名不能为空");
        }
        try {
            // 1. 根据对象名读取 MinIO 文件流
            return getMinioClient().getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception exception) {
            throw new ServiceException("从 MinIO 读取文件失败，objectName=" + objectName,
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 删除 MinIO 文件。
     *
     * @param objectName 对象名称
     */
    @Override
    public void delete(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            throw new ServiceException("MinIO 对象名不能为空");
        }
        try {
            // 1. 根据对象名删除 MinIO 文件
            getMinioClient().removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .build());
            log.info("删除 MinIO 文件成功，bucket={}，objectName={}", properties.getBucket(), objectName);
        } catch (Exception exception) {
            throw new ServiceException("删除 MinIO 文件失败，objectName=" + objectName,
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private void validateSaveArguments(String fileName, InputStream inputStream, long size) {
        if (!StringUtils.hasText(fileName)) {
            throw new ServiceException("文件名不能为空");
        }
        if (inputStream == null) {
            throw new ServiceException("文件输入流不能为空");
        }
        if (size < 0) {
            throw new ServiceException("文件大小不能小于0");
        }
    }

    private void ensureBucketExists(MinioClient client) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucket())
                    .build());
            if (!exists && properties.isCreateBucket()) {
                client.makeBucket(MakeBucketArgs.builder()
                        .bucket(properties.getBucket())
                        .build());
                log.info("MinIO 存储桶不存在，已自动创建，bucket={}", properties.getBucket());
            }
            if (!exists && !properties.isCreateBucket()) {
                throw new ServiceException("MinIO 存储桶不存在，bucket=" + properties.getBucket());
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("检查 MinIO 存储桶失败，bucket=" + properties.getBucket(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private MinioClient getMinioClient() {
        if (minioClient == null) {
            synchronized (this) {
                if (minioClient == null) {
                    minioClient = MinioClient.builder()
                            .endpoint(properties.getEndpoint())
                            .credentials(properties.getAccessKey(), properties.getSecretKey())
                            .build();
                }
            }
        }
        return minioClient;
    }

    private String buildObjectUrl(String objectName) {
        String normalizedEndpoint = properties.getEndpoint().endsWith("/")
                ? properties.getEndpoint().substring(0, properties.getEndpoint().length() - 1)
                : properties.getEndpoint();
        return normalizedEndpoint + "/" + properties.getBucket() + "/" + objectName;
    }
}
