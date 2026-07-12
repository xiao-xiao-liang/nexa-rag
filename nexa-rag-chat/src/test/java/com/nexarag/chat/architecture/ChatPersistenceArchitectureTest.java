package com.nexarag.chat.architecture;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.chat.mapper.ChatConversationMapper;
import com.nexarag.chat.mapper.ChatConversationSummaryMapper;
import com.nexarag.chat.mapper.ChatMessageMapper;
import com.nexarag.chat.service.impl.ConversationMessageServiceImpl;
import com.nexarag.chat.service.impl.ConversationServiceImpl;
import com.nexarag.chat.service.impl.ConversationSummaryServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验会话持久化分层是否符合项目 MyBatis-Plus 规范。
 */
class ChatPersistenceArchitectureTest {

    @Test
    void serviceImplementationsShouldExtendMybatisPlusServiceImpl() {
        assertThat(ConversationServiceImpl.class).isAssignableTo(ServiceImpl.class);
        assertThat(ConversationMessageServiceImpl.class).isAssignableTo(ServiceImpl.class);
        assertThat(ConversationSummaryServiceImpl.class).isAssignableTo(ServiceImpl.class);
    }

    @Test
    void mappersShouldOnlyExposeBaseMapperMethods() {
        assertThat(ChatConversationMapper.class.getDeclaredMethods()).isEmpty();
        assertThat(ChatMessageMapper.class.getDeclaredMethods()).isEmpty();
        assertThat(ChatConversationSummaryMapper.class.getDeclaredMethods()).isEmpty();
    }
}
