package com.nexarag.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.chat.entity.ChatConversationSummary;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话摘要数据库访问 Mapper。
 */
@Mapper
public interface ChatConversationSummaryMapper extends BaseMapper<ChatConversationSummary> {
}
