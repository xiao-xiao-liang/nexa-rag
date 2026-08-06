package com.nexarag.document.splitter.support;

/**
 * 文本窗口在原始文本中的半开区间，用于在保留原文片段时关联对应的源位置。
 *
 * @param startOffset 起始偏移量（包含）
 * @param endOffset   结束偏移量（不包含）
 */
public record TextWindowRange(int startOffset, int endOffset) {
}
