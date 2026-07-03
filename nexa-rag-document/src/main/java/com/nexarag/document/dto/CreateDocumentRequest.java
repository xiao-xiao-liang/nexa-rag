package com.nexarag.document.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建文档请求。
 *
 * @param title            文档标题
 * @param description      文档描述
 * @param originalFileName 原始文件名
 * @param originalFileUrl  原始文件地址
 * @param fileSize         文件大小
 */
public record CreateDocumentRequest(
        @NotBlank(message = "文档标题不能为空") String title,
        String description,
        @NotBlank(message = "原始文件名不能为空") String originalFileName,
        @NotBlank(message = "原始文件地址不能为空") String originalFileUrl,
        Long fileSize) {
}
