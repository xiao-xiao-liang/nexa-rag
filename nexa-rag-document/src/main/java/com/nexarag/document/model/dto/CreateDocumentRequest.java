package com.nexarag.document.model.dto;

import com.nexarag.infra.enums.ExternalDocumentSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 创建文档请求。
 *
 * @param title              文档标题
 * @param description        文档描述
 * @param originalFileName   原始文件名
 * @param originalObjectName 原始文件对象名
 * @param originalFileUrl    原始文件地址
 * @param fileSize           文件大小
 */
public record CreateDocumentRequest(
        @NotBlank(message = "文档标题不能为空")
        @Size(max = 256, message = "文档标题不能超过256个字符")
        String title,
        @Size(max = 1024, message = "文档描述不能超过1024个字符")
        String description,
        @NotBlank(message = "原始文件名不能为空")
        @Size(max = 512, message = "原始文件名不能超过512个字符")
        String originalFileName,
        @Size(max = 1024, message = "原始文件对象名不能超过1024个字符")
        String originalObjectName,
        @Size(max = 1024, message = "原始文件地址不能超过1024个字符")
        String originalFileUrl,
        @PositiveOrZero(message = "文件大小不能小于0")
        Long fileSize,
        ExternalDocumentSourceType sourceType,
        @Size(max = 1024, message = "来源URL不能超过1024个字符")
        String sourceUrl) {

    /**
     * 兼容本地上传创建请求。
     */
    public CreateDocumentRequest(String title, String description, String originalFileName,
                                 String originalObjectName, String originalFileUrl, Long fileSize) {
        this(title, description, originalFileName, originalObjectName, originalFileUrl, fileSize,
                ExternalDocumentSourceType.LOCAL, null);
    }

    /**
     * 创建外部文档请求，远端内容会在异步解析阶段读取。
     */
    public static CreateDocumentRequest external(String title, String description, String virtualFileName,
                                                 ExternalDocumentSourceType sourceType, String sourceUrl) {
        return new CreateDocumentRequest(title, description, virtualFileName, null, null, null,
                sourceType, sourceUrl);
    }
}
