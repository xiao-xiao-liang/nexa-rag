package com.nexarag.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.chat.domain.ChatConversationSummaryVO;
import com.nexarag.chat.entity.ChatConversationSummary;

/**
 * 管理会话摘要生成和版本。
 */
public interface ConversationSummaryService extends IService<ChatConversationSummary> {

    /** 异步调度会话摘要生成任务。 */
    void scheduleIfNecessary(String conversationId, String userId);

    /** 同步生成会话摘要。 */
    ChatConversationSummaryVO generate(String conversationId, String userId);

    /** 查询会话最新摘要。 */
    ChatConversationSummaryVO getLatest(String conversationId, String userId);
}
