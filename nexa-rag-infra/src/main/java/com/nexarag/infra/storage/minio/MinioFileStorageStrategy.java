package com.nexarag.infra.storage.minio;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.StorageProperties;
import com.nexarag.infra.enums.StorageType;
import com.nexarag.infra.storage.FileStorageStrategy;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.StoredFile;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.ListObjectsArgs;
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
     * 保存原始文件到 MinIO。
     *
     * @param fileName    文件名
     * @param inputStream 文件输入流
     * @param size        文件大小
     * @return 已存储文件信息
     */
    @Override
    public StoredFile save(String fileName, InputStream inputStream, long size) {
        // 1. 校验上传文件基础参数
        validateOriginalSaveArguments(fileName, inputStream, size);

        // 2. 生成原始文件对象名
        String objectName = objectNameResolver.resolveOriginalObjectName(fileName);

        // 3. 按对象名保存原始文件
        return saveObject(objectName, inputStream, size, CONTENT_TYPE_OCTET_STREAM);
    }

    /**
     * 按指定对象名保存文件到 MinIO。
     *
     * @param objectName  对象名
     * @param inputStream 文件输入流
     * @param size        文件大小
     * @param contentType 内容类型
     * @return 已存储文件信息
     */
    @Override
    public StoredFile saveAs(String objectName, InputStream inputStream, long size, String contentType) {
        // 1. 校验指定对象保存参数
        validateSaveAsArguments(objectName, inputStream, size);

        // 2. 使用调用方指定对象名保存文件
        String safeContentType = StringUtils.hasText(contentType) ? contentType : CONTENT_TYPE_OCTET_STREAM;
        return saveObject(objectName, inputStream, size, safeContentType);
    }

    /**
     * 从 MinIO 读取文件。
     *
     * @param objectName 对象名
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
     * 解析 MinIO 对象的可访问地址。
     *
     * @param objectName 对象名
     * @return 对象访问地址
     */
    @Override
    public String resolveUrl(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            throw new ServiceException("MinIO 对象名不能为空");
        }
        return buildObjectUrl(objectName);
    }

    /**
     * 删除 MinIO 文件。
     *
     * @param objectName 对象名
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

    /**
     * 删除指定对象前缀下的全部 MinIO 文件。
     *
     * @param objectPrefix 已校验的对象前缀
     */
    @Override
    public void deleteByPrefix(String objectPrefix) {
        if (!StringUtils.hasText(objectPrefix)) {
            throw new ServiceException("MinIO 对象前缀不能为空");
        }
        try {
            // 1. 递归列举前缀下对象，避免执行桶级删除
            Iterable<io.minio.Result<io.minio.messages.Item>> results = getMinioClient().listObjects(
                    ListObjectsArgs.builder().bucket(properties.getBucket()).prefix(objectPrefix).recursive(true).build());

            // 2. 逐个删除对象，便于失败时由上层重试
            for (io.minio.Result<io.minio.messages.Item> result : results) {
                String objectName = result.get().objectName();
                getMinioClient().removeObject(RemoveObjectArgs.builder()
                        .bucket(properties.getBucket()).object(objectName).build());
            }
            log.info("删除 MinIO 前缀文件成功，bucket={}，objectPrefix={}", properties.getBucket(), objectPrefix);
        } catch (Exception exception) {
            throw new ServiceException("删除 MinIO 前缀文件失败，objectPrefix=" + objectPrefix,
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private StoredFile saveObject(String objectName, InputStream inputStream, long size, String contentType) {
        MinioClient client = getMinioClient();
        ensureBucketExists(client);
        try {
            // 1. 上传文件流到 MinIO
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .stream(inputStream, size, UNKNOWN_PART_SIZE)
                    .contentType(contentType)
                    .build());
            log.info("文件保存到 MinIO 成功，bucket={}，objectName={}，size={}，contentType={}",
                    properties.getBucket(), objectName, size, contentType);
            return new StoredFile(objectName, buildObjectUrl(objectName), size);
        } catch (Exception exception) {
            throw new ServiceException("文件保存到 MinIO 失败，objectName=" + objectName,
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private void validateOriginalSaveArguments(String fileName, InputStream inputStream, long size) {
        if (!StringUtils.hasText(fileName)) {
            throw new ServiceException("文件名不能为空");
        }
        validateInputStreamAndSize(inputStream, size);
    }

    private void validateSaveAsArguments(String objectName, InputStream inputStream, long size) {
        if (!StringUtils.hasText(objectName)) {
            throw new ServiceException("MinIO 对象名不能为空");
        }
        validateInputStreamAndSize(inputStream, size);
    }

    private void validateInputStreamAndSize(InputStream inputStream, long size) {
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
