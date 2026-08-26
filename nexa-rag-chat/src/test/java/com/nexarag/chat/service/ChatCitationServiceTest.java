package com.nexarag.chat.service;

import com.nexarag.chat.domain.ChatCitationDTO;
import com.nexarag.chat.domain.ChatCitationSetCodec;
import com.nexarag.chat.domain.ChatCitationSetDTO;
import com.nexarag.chat.entity.ChatMessage;
import com.nexarag.chat.enums.ChatMessageRole;
import com.nexarag.chat.service.impl.ChatCitationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 消息引用归属和公开摘要测试。
 */
class ChatCitationServiceTest {

    @Test
    void shouldExposeOnlyCitationIdsAndResolveOwnedCitation() {
        ConversationMessageService messageService = mock(ConversationMessageService.class);
        ChatMessage message = new ChatMessage();
        message.setMessageId("m1");
        message.setUserId("u1");
        message.setRole(ChatMessageRole.ASSISTANT.name());
        message.setReferencesJson(new ChatCitationSetCodec().encode(new ChatCitationSetDTO(1, List.of(
                new ChatCitationDTO(1, 10L, "chunk-1", 2, "费用制度", null, 1, 0.9D, "hybrid")))));
        when(messageService.getById("m1")).thenReturn(message);
        ChatCitationService service = new ChatCitationService(messageService, new ChatCitationSetCodec());

        assertThat(service.listSummaries("m1", "u1")).extracting(summary -> summary.citationId())
                .containsExactly(1);
        assertThat(service.getOwnedCitation("m1", "u1", 1).chunkId()).isEqualTo("chunk-1");
    }

    /**
     * 验证历史列表可以直接从已经查询出的消息引用 JSON 构建摘要，避免每条消息再次查询数据库。
     */
    @Test
    void shouldBuildSummariesFromReferencesJsonWithoutLoadingMessageAgain() {
        ConversationMessageService messageService = mock(ConversationMessageService.class);
        ChatCitationSetCodec codec = new ChatCitationSetCodec();
        ChatCitationService service = new ChatCitationService(messageService, codec);
        String referencesJson = codec.encode(new ChatCitationSetDTO(1, List.of(
                new ChatCitationDTO(1, 10L, "chunk-1", 2, "费用制度", null, 1, 0.9D, "hybrid"),
                new ChatCitationDTO(2, 11L, "chunk-2", 3, "采购制度", null, 1, 0.8D, "hybrid"))));

        assertThat(service.listSummariesByReferencesJson(referencesJson))
                .extracting(summary -> summary.citationId())
                .containsExactly(1, 2);
        verifyNoInteractions(messageService);
    }
}
