package com.nexarag.document.model.dto;

import jakarta.validation.constraints.Size;

/**
 * 正则文本切分参数。
 *
 * @param separator     普通分隔符
 * @param regex         正则表达式
 * @param keepSeparator 是否保留分隔符
 */
public record RegexSplitOptions(String separator,
                                @Size(max = 256, message = "正则表达式不能超过256个字符") String regex,
                                Boolean keepSeparator) {
}
