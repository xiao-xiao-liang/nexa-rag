package com.nexarag.infra.storage;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 对象名解析器，负责生成安全且可按日期归档的存储对象路径。
 */
@Component
public class ObjectNameResolver {

    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

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

    private String extractSimpleFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "file";
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
}
