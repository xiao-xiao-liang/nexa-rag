package com.nexarag.chat.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.chat.cache.ConversationContextLock;
import com.nexarag.chat.constants.ChatContextConstants;
import com.nexarag.chat.domain.ChatConversationSummaryVO;
import com.nexarag.chat.domain.ChatMessageVO;
import com.nexarag.chat.entity.ChatConversationSummary;
import com.nexarag.chat.enums.ChatMessageRole;
import com.nexarag.chat.id.ChatIdGenerator;
import com.nexarag.chat.mapper.ChatConversationSummaryMapper;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.chat.service.ConversationService;
import com.nexarag.chat.service.ConversationSummaryService;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelMessage;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.IntStream;

import static com.nexarag.chat.constants.ChatContextConstants.SUMMARY_MAX_CHARS;
import static com.nexarag.chat.constants.ChatContextConstants.SUMMARY_ROUTE_KEY;

/**
 * 会话摘要服务，负责摘要生成、版本保存和异步调度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryServiceImpl extends ServiceImpl<ChatConversationSummaryMapper, ChatConversationSummary>
        implements ConversationSummaryService {

    private static final int SUMMARY_MESSAGE_LIMIT = 1000;

    private final ConversationService conversationService;
    private final ConversationMessageService messageService;
    private final ModelGateway modelGateway;
    private final ChatIdGenerator chatIdGenerator;
    private final ConversationContextLock contextLock;
    private final Semaphore chatSummarySemaphore;

    @Override
    @Async(ChatContextConstants.SUMMARY_EXECUTOR_NAME)
    public void scheduleIfNecessary(String conversationId, String userId) {
        if (!chatSummarySemaphore.tryAcquire()) {
            log.info("会话摘要任务已达到并发上限，跳过本次调度, conversationId={}, userId={}", conversationId, userId);
            return;
        }
        try {
            generate(conversationId, userId);
        } catch (RuntimeException exception) {
            log.warn("异步生成会话摘要失败, conversationId={}, userId={}", conversationId, userId, exception);
        } finally {
            chatSummarySemaphore.release();
        }
    }

    @Override
    public ChatConversationSummaryVO generate(String conversationId, String userId) {
        conversationService.getOwned(conversationId, userId);
        return contextLock.execute(userId, conversationId, () -> generateUnlocked(conversationId, userId));
    }

    @Override
    public ChatConversationSummaryVO getLatest(String conversationId, String userId) {
        conversationService.getOwned(conversationId, userId);
        return findLatestEntity(conversationId, userId).map(this::toVO).orElse(null);
    }

    private ChatConversationSummaryVO generateUnlocked(String conversationId, String userId) {
        ChatConversationSummaryVO latest = findLatestEntity(conversationId, userId)
                .map(this::toVO)
                .orElse(null);
        List<ChatMessageVO> messages = messageService.listHistory(conversationId, userId, SUMMARY_MESSAGE_LIMIT)
                .stream()
                .filter(ChatMessageVO::usableForContext)
                .toList();
        List<ChatMessageVO> newMessages = messagesAfterSummary(messages, latest);
        long totalUserTurns = messages.stream()
                .filter(message -> message.role() == ChatMessageRole.USER)
                .count();
        if (newMessages.isEmpty() || totalUserTurns < ChatContextConstants.SUMMARY_START_TURNS) {
            return latest;
        }

        List<ChatModelMessage> promptMessages = new ArrayList<>();
        promptMessages.add(new ChatModelMessage("SYSTEM", "请将以下会话压缩为事实、偏好、任务和结论摘要，摘要不超过"
                + SUMMARY_MAX_CHARS + "字。"));
        if (latest != null && latest.content() != null && !latest.content().isBlank()) {
            promptMessages.add(new ChatModelMessage("ASSISTANT", "已有摘要：" + latest.content()));
        }
        promptMessages.addAll(newMessages.stream()
                .map(message -> new ChatModelMessage(message.role().name(), message.content()))
                .toList());

        ChatModelResponse response = modelGateway.chat(ChatModelRequest.builder()
                .traceId(UUID.randomUUID().toString())
                .bizType(ModelBizType.CHAT)
                .bizId(conversationId)
                .routeKey(SUMMARY_ROUTE_KEY)
                .messages(promptMessages)
                .options(Map.of("temperature", 0.3D))
                .build());
        String content = response == null ? null : response.content();
        if (StrUtil.isBlank(content)) {
            return latest;
        }
        String normalized = content.trim();
        if (normalized.length() > SUMMARY_MAX_CHARS) {
            normalized = normalized.substring(0, SUMMARY_MAX_CHARS);
        }
        ChatMessageVO lastMessage = newMessages.getLast();
        long version = latest == null ? 1L : latest.summaryVersion() + 1L;
        ChatConversationSummary summary = ChatConversationSummary.builder()
                .summaryId(chatIdGenerator.nextId())
                .conversationId(conversationId)
                .userId(userId)
                .content(normalized)
                .lastMessageId(lastMessage.messageId())
                .summaryVersion(version)
                .build();
        save(summary);
        return toVO(summary);
    }

    private Optional<ChatConversationSummary> findLatestEntity(String conversationId, String userId) {
        return lambdaQuery()
                .eq(ChatConversationSummary::getConversationId, conversationId)
                .eq(ChatConversationSummary::getUserId, userId)
                .orderByDesc(ChatConversationSummary::getSummaryVersion)
                .last("LIMIT 1")
                .oneOpt();
    }

    private List<ChatMessageVO> messagesAfterSummary(List<ChatMessageVO> messages, ChatConversationSummaryVO summary) {
        if (summary == null || summary.lastMessageId() == null) {
            return messages;
        }

        return IntStream.range(0, messages.size())
                .filter(index -> summary.lastMessageId().equals(messages.get(index).messageId()))
                .findFirst()
                .stream()
                .mapToObj(index -> messages.subList(index + 1, messages.size()))
                .findFirst()
                .orElse(messages);
    }

    private ChatConversationSummaryVO toVO(ChatConversationSummary entity) {
        return new ChatConversationSummaryVO(
                entity.getSummaryId(),
                entity.getConversationId(),
                entity.getUserId(),
                entity.getContent(),
                entity.getLastMessageId(),
                entity.getSummaryVersion(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
