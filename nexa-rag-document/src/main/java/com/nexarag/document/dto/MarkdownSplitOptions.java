package com.nexarag.document.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Markdown 文档切分参数。
 *
 * @param titleLevel                参与切分的最大标题层级
 * @param stripHeaders              是否从片段正文中移除标题行
 * @param preserveCodeBlock         是否保护代码块内的标题符号
 * @param createParentForOversized  超长片段是否创建父片段
 */
public record MarkdownSplitOptions(
        @Min(value = 1, message = "标题层级不能小于1")
        @Max(value = 6, message = "标题层级不能超过6")
        Integer titleLevel,
        Boolean stripHeaders,
        Boolean preserveCodeBlock,
        Boolean createParentForOversized) {
}
