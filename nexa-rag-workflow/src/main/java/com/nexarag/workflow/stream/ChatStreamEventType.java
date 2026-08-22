package com.nexarag.workflow.stream;

/**
 * Chat 流式输出事件类型。
 */
public enum ChatStreamEventType {
    META,
    SNAPSHOT,
    CITATIONS,
    ANSWER_DELTA,
    TOKEN,
    COMPLETE,
    ERROR,
    CANCELLED
}
