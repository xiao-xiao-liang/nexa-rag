package com.nexarag.chat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexarag.auth.context.CurrentUser;
import com.nexarag.auth.context.CurrentUserContext;
import com.nexarag.chat.domain.ConversationHistoryPageVO;
import com.nexarag.chat.domain.ConversationMessageItemVO;
import com.nexarag.chat.domain.ChatConversationVO;
import com.nexarag.chat.domain.ChatMessageVO;
import com.nexarag.chat.enums.ChatMessageRole;
import com.nexarag.chat.enums.ChatMessageStatus;
import com.nexarag.chat.enums.ConversationStatus;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.chat.service.ConversationService;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.web.CursorPageVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 会话查询控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

    @Mock
    private ConversationService conversationService;
    @Mock
    private ConversationMessageService messageService;
    @InjectMocks
    private ConversationController controller;

    @AfterEach
    void clearCurrentUser() {
        CurrentUserContext.clear();
    }

    @Test
    void listShouldPassCurrentUserAndReturnSafeConversationProjection() {
        CurrentUserContext.set(new CurrentUser("u1"));
        Page<ChatConversationVO> page = Page.of(1, 20, 1);
        page.setRecords(List.of(ChatConversationVO.builder()
                .conversationId("c1")
                .userId("u1")
                .title("测试会话")
                .status(ConversationStatus.ACTIVE)
                .lastMessageId("m1")
                .lastMessageTime(LocalDateTime.of(2026, 7, 26, 9, 0))
                .version(3)
                .createdTime(LocalDateTime.of(2026, 7, 25, 9, 0))
                .updatedTime(LocalDateTime.of(2026, 7, 26, 9, 0))
                .build()));
        when(conversationService.pageByUser("u1", 1, 20)).thenReturn(page);

        var response = controller.list(1, 20);

        assertThat(response.data().getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getConversationId()).isEqualTo("c1");
            assertThat(item.getTitle()).isEqualTo("测试会话");
        });
        assertThat(response.data().getRecords().getFirst().getClass().getDeclaredFields())
                .extracting(field -> field.getName())
                .containsExactlyInAnyOrder("conversationId", "title", "status", "lastMessageTime",
                        "createdTime", "updatedTime");
        verify(conversationService).pageByUser("u1", 1, 20);
    }

    @Test
    void historyShouldPassCurrentUserAndCursorToService() {
        CurrentUserContext.set(new CurrentUser("u1"));
        ChatMessageVO message = new ChatMessageVO("m1", "c1", "u1", 8L,
                ChatMessageRole.ASSISTANT, ChatMessageStatus.FAILED, "已生成内容", "思考内容",
                "[{\"source\":\"内部\"}]", "g1", "[{\"opId\":\"g1:tool:1\"}]", 10, 20, 30,
                "MODEL_ERROR", "内部失败详情",
                LocalDateTime.of(2026, 7, 26, 9, 0), LocalDateTime.of(2026, 7, 26, 9, 1));
        when(messageService.pageHistory("c1", "u1", 8L, 50))
                .thenReturn(new CursorPageVO<>(List.of(message), false, 8L));

        var response = controller.history("c1", 8L, 50);

        assertThat(response.data()).isInstanceOf(ConversationHistoryPageVO.class);
        assertThat(response.data().getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getContent()).isEqualTo("已生成内容");
            assertThat(item.getStatus()).isEqualTo(ChatMessageStatus.FAILED);
            assertThat(item.getGenerationId()).isEqualTo("g1");
            assertThat(item.getToolOperationsJson()).isEqualTo("[{\"opId\":\"g1:tool:1\"}]");
        });
        assertThat(ConversationMessageItemVO.class.getDeclaredFields())
                .extracting(field -> field.getName())
                .containsExactlyInAnyOrder("messageId", "sequence", "role", "status", "content",
                        "generationId", "toolOperationsJson", "createdTime", "updatedTime");
        verify(messageService).pageHistory("c1", "u1", 8L, 50);
    }

    @Test
    void shouldRejectInvalidPaginationAndCursorParametersBeforeCallingServices() {
        assertThatThrownBy(() -> controller.list(0, 20))
                .isInstanceOf(ClientException.class)
                .hasMessage("当前页码必须在 1 到 1000 之间");
        assertThatThrownBy(() -> controller.list(1, 101))
                .isInstanceOf(ClientException.class)
                .hasMessage("会话列表每页数量必须在 1 到 100 之间");
        assertThatThrownBy(() -> controller.history("c1", 0L, 50))
                .isInstanceOf(ClientException.class)
                .hasMessage("历史消息游标必须大于 0");
        assertThatThrownBy(() -> controller.history("c1", null, 1001))
                .isInstanceOf(ClientException.class)
                .hasMessage("历史消息每页数量必须在 1 到 1000 之间");
        verifyNoInteractions(conversationService, messageService);
    }

    @Test
    void listShouldReturnEmptyPageWithoutChangingPaginationMetadata() {
        CurrentUserContext.set(new CurrentUser("u1"));
        Page<ChatConversationVO> page = Page.of(3, 20, 0);
        when(conversationService.pageByUser("u1", 3, 20)).thenReturn(page);

        var response = controller.list(3, 20);

        assertThat(response.data().getRecords()).isEmpty();
        assertThat(response.data().getCurrent()).isEqualTo(3);
        assertThat(response.data().getSize()).isEqualTo(20);
        assertThat(response.data().getTotal()).isZero();
        verify(conversationService).pageByUser("u1", 3, 20);
    }

    @Test
    void historyShouldReadLatestPageWhenCursorIsMissing() {
        CurrentUserContext.set(new CurrentUser("u1"));
        when(messageService.pageHistory("c1", "u1", null, 50))
                .thenReturn(new CursorPageVO<>(List.of(), false, null));

        var response = controller.history("c1", null, 50);

        assertThat(response.data().getRecords()).isEmpty();
        assertThat(response.data().isHasMore()).isFalse();
        assertThat(response.data().getNextBeforeSequence()).isNull();
        verify(messageService).pageHistory("c1", "u1", null, 50);
    }

    @Test
    void historyShouldPropagateConversationOwnershipFailure() {
        CurrentUserContext.set(new CurrentUser("u1"));
        ClientException expected = new ClientException("会话不存在或不属于当前用户");
        when(messageService.pageHistory("c1", "u1", null, 50)).thenThrow(expected);

        assertThatThrownBy(() -> controller.history("c1", null, 50))
                .isSameAs(expected);
        verify(messageService).pageHistory("c1", "u1", null, 50);
    }
}
