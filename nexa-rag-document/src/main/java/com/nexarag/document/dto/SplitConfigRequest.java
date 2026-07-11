package com.nexarag.document.dto;

import com.nexarag.document.enums.SplitStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 文档切分配置请求。
 *
 * @param splitStrategy 切分策略
 * @param chunkSize     片段大小
 * @param chunkOverlap  片段重叠大小
 * @param markdown      Markdown 切分参数
 * @param regex         正则文本切分参数
 * @param excel         Excel/CSV 切分参数
 */
public record SplitConfigRequest(
        @NotNull(message = "切分策略不能为空")
        SplitStrategy splitStrategy,
        @Min(value = 1, message = "片段大小不能小于1")
        @Max(value = 20000, message = "片段大小不能超过20000")
        Integer chunkSize,
        @Min(value = 0, message = "片段重叠大小不能小于0")
        @Max(value = 5000, message = "片段重叠大小不能超过5000")
        Integer chunkOverlap,
        @Valid MarkdownSplitOptions markdown,
        @Valid RegexSplitOptions regex,
        @Valid ExcelSplitOptions excel) {

    /**
     * 兼容阶段二仅传基础切分配置的构造方式。
     *
     * @param splitStrategy 切分策略
     * @param chunkSize     片段大小
     * @param chunkOverlap  片段重叠大小
     */
    public SplitConfigRequest(SplitStrategy splitStrategy, Integer chunkSize, Integer chunkOverlap) {
        this(splitStrategy, chunkSize, chunkOverlap, null, null, null);
    }
}
