package com.nexarag.workflow.stream;

import java.util.List;

/**
 * 保存并发布可按版本恢复的 Chat 流事件。
 */
public interface ChatStreamEventBuffer {

    /**
     * 为事件分配版本，写入重放缓冲并发布跨实例通知。
     *
     * @param event 待发布事件
     * @return 已分配版本的事件
     */
    ChatStreamEvent publish(ChatStreamEvent event);

    /**
     * 读取版本号严格大于指定值的已缓存事件。
     *
     * @param generationId 生成任务 ID
     * @param eventVersion 已接收的最大事件版本
     * @return 升序事件列表
     */
    List<ChatStreamEvent> eventsAfter(String generationId, long eventVersion);
}
