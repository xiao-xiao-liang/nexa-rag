package com.nexarag.boot.constants;

/**
 * 对话 HTTP 接口使用的稳定路径片段。
 */
public final class ChatApiPathConstant {

    public static final String ROOT = "/api/chat";
    public static final String STREAM = "/stream";
    public static final String GENERATION_STREAM = "/generations/{generationId}/stream";
    public static final String GENERATION = "/generations/{generationId}";
    public static final String CITATION = "/messages/{messageId}/citations/{citationId}";
    public static final String KNOWLEDGE_BASE_DOCUMENT_PREFIX = "/knowledge-base/";
    public static final String DOCUMENT_PREFIX = "/documents/";

    private ChatApiPathConstant() {
    }
}
