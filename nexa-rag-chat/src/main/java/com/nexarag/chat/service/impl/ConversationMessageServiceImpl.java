package com.nexarag.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.chat.cache.ConversationContextLock;
import com.nexarag.chat.domain.ChatGenerationTurnBO;
import com.nexarag.chat.domain.ChatMessageVO;
import com.nexarag.chat.entity.ChatConversation;
import com.nexarag.chat.entity.ChatMessage;
import com.nexarag.chat.enums.ChatMessageRole;
import com.nexarag.chat.enums.ChatMessageStatus;
import com.nexarag.chat.id.ChatIdGenerator;
import com.nexarag.chat.mapper.ChatMessageMapper;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.chat.service.ConversationService;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.web.CursorPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static com.nexarag.chat.constants.ConversationQueryConstants.MAX_HISTORY_PAGE_SIZE;

/**
 * 消息生命周期服务，负责消息创建、状态流转和历史查询。
 */
@Service
@RequiredArgsConstructor
public class ConversationMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
        implements ConversationMessageService {

    private final ConversationService conversationService;
    private final ChatIdGenerator chatIdGenerator;
    private final ConversationContextLock contextLock;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO appendUserMessage(String conversationId, String userId, String content) {
        validateContent(content);
        return contextLock.execute(userId, conversationId, () -> {
            // 1. 校验会话归属并查询下一个消息序号
            ChatConversation conversation = requireConversationEntity(conversationId, userId);
            long sequence = nextSequence(conversationId, userId);

            // 2. 创建并保存已完成的用户消息
            LocalDateTime now = LocalDateTime.now();
            ChatMessage message = buildMessage(conversationId, userId, sequence,
                    ChatMessageRole.USER, ChatMessageStatus.COMPLETED, content, now);
            save(message);

            // 3. 更新会话最近消息信息
            conversation.setLastMessageId(message.getMessageId());
            conversation.setLastMessageTime(now);
            updateByConversation(conversation);
            return toDomain(message);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO startAssistantMessage(String conversationId, String userId) {
        return contextLock.execute(userId, conversationId, () -> {
            // 1. 校验会话归属并查询下一个消息序号
            requireConversationEntity(conversationId, userId);
            long sequence = nextSequence(conversationId, userId);

            // 2. 保存生成中的助手消息占位记录
            LocalDateTime now = LocalDateTime.now();
            ChatMessage message = buildMessage(conversationId, userId, sequence,
                    ChatMessageRole.ASSISTANT, ChatMessageStatus.GENERATING, null, now);
            save(message);
            return toDomain(message);
        });
    }

    /**
     * 在会话锁内创建本轮用户消息和助手占位消息，避免同会话并发生成。
     *
     * @param conversationId 会话 ID
     * @param userId 用户 ID
     * @param userContent 用户问题
     * @param generationId 生成任务 ID
     * @return 本轮创建的两条消息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatGenerationTurnBO beginGenerationTurn(String conversationId, String userId,
                                                     String userContent, String generationId) {
        validateContent(userContent);
        validateGenerationId(generationId);
        return contextLock.execute(userId, conversationId, () -> {
            // 1. 校验会话归属并拒绝已有的活动生成
            ChatConversation conversation = requireConversationEntity(conversationId, userId);
            if (hasGeneratingAssistantMessage(conversationId, userId)) {
                throw new ClientException("当前会话已有进行中的回答，请等待完成后再发送");
            }

            // 2. 按顺序保存用户消息与助手占位消息
            LocalDateTime now = LocalDateTime.now();
            long userSequence = nextSequence(conversationId, userId);
            ChatMessage userMessage = buildMessage(conversationId, userId, userSequence,
                    ChatMessageRole.USER, ChatMessageStatus.COMPLETED, userContent, now);
            save(userMessage);
            ChatMessage assistantMessage = buildMessage(conversationId, userId, userSequence + 1,
                    ChatMessageRole.ASSISTANT, ChatMessageStatus.GENERATING, null, now);
            assistantMessage.setGenerationId(generationId);
            save(assistantMessage);

            // 3. 更新会话最新用户消息并返回本轮消息
            conversation.setLastMessageId(userMessage.getMessageId());
            conversation.setLastMessageTime(now);
            updateByConversation(conversation);
            return new ChatGenerationTurnBO(toDomain(userMessage), toDomain(assistantMessage));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeAssistantMessage(String messageId, String content, String thinkingContent,
                                         Integer promptTokens, Integer completionTokens, Integer totalTokens,
                                         String referencesJson) {
        completeAssistantMessage(messageId, content, thinkingContent, promptTokens, completionTokens, totalTokens,
                referencesJson, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeAssistantMessage(String messageId, String content, String thinkingContent,
                                         Integer promptTokens, Integer completionTokens, Integer totalTokens,
                                         String referencesJson, String toolOperationsJson) {
        validateContent(content);
        // 1. 组装完成状态和模型用量
        ChatMessage message = new ChatMessage();
        message.setStatus(ChatMessageStatus.COMPLETED.name());
        message.setContent(content);
        message.setThinkingContent(thinkingContent);
        message.setReferencesJson(referencesJson);
        message.setToolOperationsJson(toolOperationsJson);
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setTotalTokens(totalTokens);
        message.setFailureCode(null);
        message.setFailureMessage(null);
        // 2. 仅允许生成中的消息进入完成状态
        updateGeneratingMessage(messageId, message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void failAssistantMessage(String messageId, String partialContent,
                                     String failureCode, String failureMessage) {
        failAssistantMessage(messageId, partialContent, failureCode, failureMessage, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void failAssistantMessage(String messageId, String partialContent,
                                     String failureCode, String failureMessage, String toolOperationsJson) {
        failAssistantMessage(messageId, partialContent, failureCode, failureMessage, null, toolOperationsJson);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void failAssistantMessage(String messageId, String partialContent,
                                     String failureCode, String failureMessage, String referencesJson,
                                     String toolOperationsJson) {
        // 1. 组装失败状态和已生成的部分回答
        ChatMessage message = new ChatMessage();
        message.setStatus(ChatMessageStatus.FAILED.name());
        message.setContent(partialContent);
        message.setFailureCode(failureCode);
        message.setFailureMessage(failureMessage);
        message.setReferencesJson(referencesJson);
        message.setToolOperationsJson(toolOperationsJson);

        // 2. 仅允许生成中的消息进入失败状态
        updateGeneratingMessage(messageId, message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAssistantMessage(String messageId, String partialContent) {
        cancelAssistantMessage(messageId, partialContent, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAssistantMessage(String messageId, String partialContent, String toolOperationsJson) {
        cancelAssistantMessage(messageId, partialContent, null, toolOperationsJson);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAssistantMessage(String messageId, String partialContent, String referencesJson,
                                       String toolOperationsJson) {
        // 1. 组装取消状态和已生成的部分回答
        ChatMessage message = new ChatMessage();
        message.setStatus(ChatMessageStatus.CANCELLED.name());
        message.setContent(partialContent);
        message.setReferencesJson(referencesJson);
        message.setToolOperationsJson(toolOperationsJson);

        // 2. 仅允许生成中的消息进入取消状态
        updateGeneratingMessage(messageId, message);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageVO> listHistory(String conversationId, String userId, int limit) {
        requireConversationEntity(conversationId, userId);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_HISTORY_PAGE_SIZE);
        return baseMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getUserId, userId)
                .orderByDesc(ChatMessage::getSequence)
                .last("LIMIT " + safeLimit))
                .stream()
                .sorted(Comparator.comparingLong(ChatMessage::getSequence))
                .map(this::toDomain)
                .toList();
    }

    /**
     * 按消息序号向前查询历史消息，并返回前端可继续使用的游标。
     *
     * @param conversationId 会话 ID
     * @param userId 当前用户 ID
     * @param beforeSequence 仅查询该序号之前的消息；为空时查询最新消息
     * @param size 本次查询数量
     * @return 升序排列的历史消息游标页
     */
    @Override
    @Transactional(readOnly = true)
    public CursorPageVO<ChatMessageVO> pageHistory(String conversationId, String userId,
                                                    Long beforeSequence, int size) {
        // 1. 校验会话归属，并限制单次读取数量
        requireConversationEntity(conversationId, userId);
        int safeSize = Math.min(Math.max(size, 1), MAX_HISTORY_PAGE_SIZE);

        // 2. 按倒序多读取一条消息，以判断是否仍有更早历史
        List<ChatMessage> descendingMessages = baseMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getUserId, userId)
                .lt(beforeSequence != null, ChatMessage::getSequence, beforeSequence)
                .orderByDesc(ChatMessage::getSequence)
                .last("LIMIT " + (safeSize + 1)));
        boolean hasMore = descendingMessages.size() > safeSize;
        List<ChatMessageVO> records = descendingMessages.stream()
                .limit(safeSize)
                .sorted(Comparator.comparingLong(ChatMessage::getSequence))
                .map(this::toDomain)
                .toList();

        // 3. 使用当前批次最早消息的序号作为下一页游标
        Long nextBeforeSequence = records.isEmpty() ? null : records.getFirst().sequence();
        return new CursorPageVO<>(records, hasMore, nextBeforeSequence);
    }

    @Override
    @Transactional(readOnly = true)
    public long countCompletedUserMessagesAfterSequence(String conversationId, String userId, long afterSequence) {
        return baseMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getRole, ChatMessageRole.USER.name())
                .eq(ChatMessage::getStatus, ChatMessageStatus.COMPLETED.name())
                .gt(ChatMessage::getSequence, afterSequence));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageVO> listContextMessagesAfterSequence(String conversationId, String userId,
                                                                 long afterSequence, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_HISTORY_PAGE_SIZE);
        return baseMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .eq(ChatMessage::getUserId, userId)
                        .eq(ChatMessage::getStatus, ChatMessageStatus.COMPLETED.name())
                        .in(ChatMessage::getRole, ChatMessageRole.USER.name(), ChatMessageRole.ASSISTANT.name())
                        .gt(ChatMessage::getSequence, afterSequence)
                        .orderByAsc(ChatMessage::getSequence)
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * 查询会话内下一个消息序号。调用方必须已持有会话级锁。
     *
     * @param conversationId 会话 ID
     * @param userId 用户 ID
     * @return 下一个消息序号
     */
    private long nextSequence(String conversationId, String userId) {
        ChatMessage latest = baseMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getUserId, userId)
                .orderByDesc(ChatMessage::getSequence)
                .last("LIMIT 1"));
        return latest == null || latest.getSequence() == null ? 1L : latest.getSequence() + 1L;
    }

    private boolean hasGeneratingAssistantMessage(String conversationId, String userId) {
        return baseMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getRole, ChatMessageRole.ASSISTANT.name())
                .eq(ChatMessage::getStatus, ChatMessageStatus.GENERATING.name())) > 0;
    }

    private ChatConversation requireConversationEntity(String conversationId, String userId) {
        return conversationService.getOwnedConversation(conversationId, userId);
    }

    /**
     * 原子地最终化生成中的助手消息，已最终化的消息保持原状态。
     *
     * @param messageId 消息 ID
     * @param finalMessage 最终状态字段
     */
    private void updateGeneratingMessage(String messageId, ChatMessage finalMessage) {
        baseMapper.update(finalMessage, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getMessageId, messageId)
                .eq(ChatMessage::getStatus, ChatMessageStatus.GENERATING.name()));
    }

    private void updateByConversation(ChatConversation conversation) {
        if (!conversationService.updateById(conversation)) {
            throw new ClientException("会话已被其他请求更新，请重试");
        }
    }

    private ChatMessage buildMessage(String conversationId, String userId, long sequence,
                                     ChatMessageRole role, ChatMessageStatus status,
                                     String content, LocalDateTime now) {
        ChatMessage message = new ChatMessage();
        message.setMessageId(chatIdGenerator.nextId());
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setSequence(sequence);
        message.setRole(role.name());
        message.setStatus(status.name());
        message.setContent(content);
        message.setCreateTime(now);
        message.setUpdateTime(message.getCreateTime());
        return message;
    }

    private ChatMessageVO toDomain(ChatMessage entity) {
        return new ChatMessageVO(entity.getMessageId(), entity.getConversationId(), entity.getUserId(),
                entity.getSequence(), ChatMessageRole.valueOf(entity.getRole()),
                ChatMessageStatus.valueOf(entity.getStatus()), entity.getContent(), entity.getThinkingContent(),
                entity.getReferencesJson(), entity.getGenerationId(), entity.getToolOperationsJson(),
                entity.getPromptTokens(), entity.getCompletionTokens(),
                entity.getTotalTokens(), entity.getFailureCode(), entity.getFailureMessage(),
                entity.getCreateTime(), entity.getUpdateTime());
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ClientException("消息内容不能为空");
        }
    }

    private void validateGenerationId(String generationId) {
        if (generationId == null || generationId.isBlank()) {
            throw new ClientException("生成任务ID不能为空");
        }
    }
}
