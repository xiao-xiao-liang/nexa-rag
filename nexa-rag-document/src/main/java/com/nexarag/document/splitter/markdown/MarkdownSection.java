package com.nexarag.document.splitter.markdown;

import java.util.List;

/**
 * Markdown 标题区块。
 *
 * @param level     标题层级
 * @param title     当前标题
 * @param titlePath 标题路径
 * @param startLine 起始行号
 * @param endLine   结束行号
 * @param text      区块文本
 */
public record MarkdownSection(int level,
                              String title,
                              List<String> titlePath,
                              int startLine,
                              int endLine,
                              String text) {
}
