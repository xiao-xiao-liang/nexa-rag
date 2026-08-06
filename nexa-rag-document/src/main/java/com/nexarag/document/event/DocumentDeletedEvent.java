package com.nexarag.document.event;

/**
 * 文档完成逻辑删除后发布的进程内事件，供下游模块清理其派生数据。
 *
 * @param documentId 已删除文档ID
 */
public record DocumentDeletedEvent(Long documentId) {
}
