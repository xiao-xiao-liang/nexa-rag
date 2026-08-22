package com.nexarag.chat.constants;

import java.time.Duration;

/**
 * 活跃会话上下文缓存常量。
 */
public final class ChatContextConstants {

    /** ChatClient 请求上下文中的会话 ID。 */
    public static final String CONVERSATION_ID_CONTEXT_KEY = "conversationId";

    public static final String CACHE_KEY_PREFIX = "nexa:chat:context:";
    public static final String CACHE_KEY_VERSION = "v1";
    public static final Duration CACHE_TTL = Duration.ofHours(24);
    public static final String SUMMARY_ROUTE_KEY = ChatModelRouteConstants.CHAT_SUMMARY_ROUTE_KEY;
    public static final int SUMMARY_INCREMENTAL_USER_TURN_THRESHOLD = 8;
    public static final int SUMMARY_MAX_CHARS = 1000;
    /** 摘要任务全局最大并发数。 */
    public static final int SUMMARY_MAX_CONCURRENCY = 16;
    /** 摘要执行器 Bean 名称。 */
    public static final String SUMMARY_EXECUTOR_NAME = "chatSummaryExecutor";

    private ChatContextConstants() {
    }
}
