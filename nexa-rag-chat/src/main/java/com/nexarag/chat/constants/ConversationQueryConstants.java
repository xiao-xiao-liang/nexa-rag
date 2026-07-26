package com.nexarag.chat.constants;

/**
 * 会话读取接口使用的分页参数限制。
 */
public final class ConversationQueryConstants {

    /** 会话列表允许的最大页码，用于限制深分页偏移量。 */
    public static final long MAX_CONVERSATION_PAGE_CURRENT = 1000L;

    /** 会话列表单页允许的最大数量。 */
    public static final long MAX_CONVERSATION_PAGE_SIZE = 100L;

    /** 历史消息单页允许的最大数量。 */
    public static final int MAX_HISTORY_PAGE_SIZE = 1000;

    private ConversationQueryConstants() {
    }
}
