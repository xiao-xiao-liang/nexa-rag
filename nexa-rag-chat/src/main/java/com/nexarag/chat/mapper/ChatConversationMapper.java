package com.nexarag.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.chat.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天会话数据库访问 Mapper。
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
}
