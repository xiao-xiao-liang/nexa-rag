package com.nexarag.chat.id;

/**
 * 生成会话领域使用的唯一 ID。
 */
public interface ChatIdGenerator {

    /**
     * 生成一个雪花算法字符串 ID。
     *
     * @return 雪花 ID 字符串
     */
    String nextId();
}
