package com.nexarag.document.dto;

/**
 * 正则文本切分参数。
 *
 * @param separator     普通分隔符
 * @param regex         正则表达式
 * @param keepSeparator 是否保留分隔符
 */
public record RegexSplitOptions(String separator, String regex, Boolean keepSeparator) {
}
