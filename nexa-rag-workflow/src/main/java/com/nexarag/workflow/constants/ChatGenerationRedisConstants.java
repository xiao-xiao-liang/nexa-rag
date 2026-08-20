package com.nexarag.workflow.constants;

import java.time.Duration;

/**
 * Chat 生成任务 Redis 键和消息主题常量。
 */
public final class ChatGenerationRedisConstants {

    public static final String CANCEL_KEY_PREFIX = "nexa:chat:generation:cancel:";
    public static final String OWNER_KEY_PREFIX = "nexa:chat:generation:owner:";
    public static final String EVENT_SEQUENCE_KEY_PREFIX = "nexa:chat:generation:event-sequence:";
    public static final String EVENT_BUFFER_KEY_PREFIX = "nexa:chat:generation:event-buffer:";
    public static final String CANCEL_TOPIC = "nexa:chat:generation:cancel";
    public static final String EVENT_TOPIC_PREFIX = "nexa:chat:generation:event:";
    public static final Duration TASK_TTL = Duration.ofMinutes(30);
    public static final long MAX_BUFFERED_EVENTS = 1_000L;

    private ChatGenerationRedisConstants() {
    }
}
