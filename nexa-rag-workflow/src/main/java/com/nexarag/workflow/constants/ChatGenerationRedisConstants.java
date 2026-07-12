package com.nexarag.workflow.constants;

import java.time.Duration;

/**
 * Chat 生成任务 Redis 键和消息主题常量。
 */
public final class ChatGenerationRedisConstants {

    public static final String CANCEL_KEY_PREFIX = "nexa:chat:generation:cancel:";
    public static final String OWNER_KEY_PREFIX = "nexa:chat:generation:owner:";
    public static final String CANCEL_TOPIC = "nexa:chat:generation:cancel";
    public static final Duration TASK_TTL = Duration.ofMinutes(30);

    private ChatGenerationRedisConstants() {
    }
}
