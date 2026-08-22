package com.nexarag.chat.service.impl;

import com.nexarag.chat.domain.ChatCitationDTO;
import com.nexarag.chat.domain.ChatCitationSetCodec;
import com.nexarag.chat.domain.ChatCitationSetDTO;
import com.nexarag.chat.domain.ChatCitationSummaryVO;
import com.nexarag.chat.entity.ChatMessage;
import com.nexarag.chat.enums.ChatMessageRole;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息引用服务，统一执行消息归属校验与引用 JSON 的安全投影。
 */
@Service
@RequiredArgsConstructor
public class ChatCitationService {

    private final ConversationMessageService messageService;
    private final ChatCitationSetCodec citationSetCodec;

    /**
     * 将引用清单编码为可持久化 JSON。
     *
     * @param citationSet 引用清单
     * @return 引用 JSON
     */
    public String serialize(ChatCitationSetDTO citationSet) {
        return citationSetCodec.encode(citationSet);
    }

    /**
     * 查询当前用户可见的消息引用编号。
     *
     * @param messageId 消息 ID
     * @param userId 当前用户 ID
     * @return 仅包含引用编号的列表
     */
    public List<ChatCitationSummaryVO> listSummaries(String messageId, String userId) {
        return citationSet(messageId, userId).citations().stream()
                .map(citation -> new ChatCitationSummaryVO(citation.citationId()))
                .toList();
    }

    /**
     * 查询当前用户可见的单条引用定位信息。
     *
     * @param messageId 消息 ID
     * @param userId 当前用户 ID
     * @param citationId 消息内引用编号
     * @return 引用定位信息
     */
    public ChatCitationDTO getOwnedCitation(String messageId, String userId, int citationId) {
        return citationSet(messageId, userId).citations().stream()
                .filter(citation -> citation.citationId() == citationId)
                .findFirst()
                .orElseThrow(() -> new ClientException("引用不存在或无权查看"));
    }

    private ChatCitationSetDTO citationSet(String messageId, String userId) {
        ChatMessage message = messageService.getById(messageId);
        if (message == null || !userId.equals(message.getUserId())
                || !ChatMessageRole.ASSISTANT.name().equals(message.getRole())) {
            throw new ClientException("消息不存在或无权查看引用");
        }
        return citationSetCodec.decode(message.getReferencesJson());
    }
}
