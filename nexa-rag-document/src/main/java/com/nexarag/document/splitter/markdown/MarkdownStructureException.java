package com.nexarag.document.splitter.markdown;

/**
 * Markdown 标题无法构成可信章节树时抛出的可恢复异常。
 */
class MarkdownStructureException extends RuntimeException {

    MarkdownStructureException(String message) {
        super(message);
    }
}
