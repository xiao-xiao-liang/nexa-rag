package com.nexarag.infra.storage;

import com.nexarag.common.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 对象名解析器，负责生成安全且可按用途归档的存储对象路径。
 */
@Component
public class ObjectNameResolver {

    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final String DEFAULT_FILE_NAME = "file";
    private static final String DEFAULT_PARSED_EXTENSION = ".txt";

    /**
     * 生成原始上传文件的对象名。
     *
     * @param fileName 原始文件名
     * @return 原始文件对象名
     */
    public String resolveOriginalObjectName(String fileName) {
        // 1. 提取文件名，避免路径穿越片段进入对象名
        String simpleFileName = extractSimpleFileName(fileName);

        // 2. 提取扩展名，保留后续解析器识别文件类型所需信息
        String extension = extractExtension(simpleFileName);

        // 3. 使用日期目录和 UUID 生成稳定唯一对象名
        String datePath = LocalDate.now().format(DATE_PATH_FORMATTER);
        return "original/" + datePath + "/" + UUID.randomUUID() + extension;
    }

    /**
     * 生成解析后主文件对象名。
     *
     * @param documentId       文档ID
     * @param originalFileName 原始文件名
     * @param extension        解析产物扩展名
     * @return 解析后主文件对象名
     */
    public String resolveParsedObjectName(Long documentId, String originalFileName, String extension) {
        // 1. 校验文档ID，解析产物必须按文档隔离
        validateDocumentId(documentId);

        // 2. 规范化扩展名，避免非法字符进入对象名
        String safeExtension = normalizeExtension(extension);

        // 3. 返回稳定解析产物路径，便于重处理覆盖和清理
        return "parsed/" + documentId + "/content" + safeExtension;
    }

    /**
     * 生成解析后资源文件对象名。
     *
     * @param documentId    文档ID
     * @param assetFileName 资源文件名
     * @return 解析后资源文件对象名
     */
    public String resolveParsedAssetObjectName(Long documentId, String assetFileName) {
        // 1. 校验文档ID，解析资源必须按文档隔离
        validateDocumentId(documentId);

        // 2. 提取安全扩展名，资源文件名主体使用 UUID 防碰撞
        String simpleFileName = extractSimpleFileName(assetFileName);
        String extension = extractExtension(simpleFileName);

        // 3. 返回资源对象名
        return "parsed/" + documentId + "/assets/" + UUID.randomUUID() + extension;
    }

    /**
     * 生成解析结构辅助制品对象名。
     *
     * @param documentId 文档ID
     * @param fileName 已验证的简单文件名
     * @return 结构制品对象名
     */
    public String resolveParsedStructureObjectName(Long documentId, String fileName) {
        validateDocumentId(documentId);
        String simpleFileName = extractSimpleFileName(fileName);
        if (!StringUtils.hasText(fileName) || !simpleFileName.equals(fileName)
                || !simpleFileName.matches("[A-Za-z0-9._-]+")) {
            throw new ServiceException("解析结构制品文件名不合法，fileName=" + fileName);
        }
        return "parsed/" + documentId + "/structure/" + simpleFileName;
    }

    /**
     * 生成指定文档全部解析制品的对象前缀。
     *
     * @param documentId 文档ID
     * @return 解析制品对象前缀
     */
    public String resolveParsedPrefix(Long documentId) {
        validateDocumentId(documentId);
        return "parsed/" + documentId + "/";
    }

    /**
     * 生成外部来源响应快照对象名。
     */
    public String resolveSourceSnapshotObjectName(Long documentId, String extension) {
        validateDocumentId(documentId);
        return resolveSourceSnapshotPrefix(documentId) + "source" + normalizeExtension(extension);
    }

    /**
     * 生成指定文档全部外部来源快照的对象前缀。
     *
     * @param documentId 文档ID
     * @return 外部来源快照对象前缀
     */
    public String resolveSourceSnapshotPrefix(Long documentId) {
        validateDocumentId(documentId);
        return "source-snapshots/" + documentId + "/";
    }

    private void validateDocumentId(Long documentId) {
        if (documentId == null) {
            throw new ServiceException("文档ID不能为空");
        }
    }

    private String extractSimpleFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return DEFAULT_FILE_NAME;
        }
        String normalizedFileName = fileName.replace('\\', '/');
        int lastSlashIndex = normalizedFileName.lastIndexOf('/');
        if (lastSlashIndex >= 0) {
            return normalizedFileName.substring(lastSlashIndex + 1);
        }
        return normalizedFileName;
    }

    private String extractExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(lastDotIndex).toLowerCase(Locale.ROOT);
        return extension.replaceAll("[^a-z0-9.]", "");
    }

    private String normalizeExtension(String extension) {
        if (!StringUtils.hasText(extension)) {
            return DEFAULT_PARSED_EXTENSION;
        }
        String safeExtension = extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
        if (!safeExtension.startsWith(".")) {
            safeExtension = "." + safeExtension;
        }
        if (".".equals(safeExtension)) {
            return DEFAULT_PARSED_EXTENSION;
        }
        return safeExtension;
    }
}
