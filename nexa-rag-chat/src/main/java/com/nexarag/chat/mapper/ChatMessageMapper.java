package com.nexarag.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息数据库访问 Mapper。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
