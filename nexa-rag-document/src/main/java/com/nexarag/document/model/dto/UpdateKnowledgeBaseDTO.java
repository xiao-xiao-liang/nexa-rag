package com.nexarag.document.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新知识库基础信息的数据传输对象。
 *
 * @param name 知识库名称
 * @param description 知识库描述
 */
public record UpdateKnowledgeBaseDTO(
        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 128, message = "知识库名称不能超过128个字符")
        String name,
        @Size(max = 1024, message = "知识库描述不能超过1024个字符")
        String description) {
}
