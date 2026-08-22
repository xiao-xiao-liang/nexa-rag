package com.nexarag.chat.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.chat.domain.ChatConversationVO;
import com.nexarag.chat.cache.ConversationContextCache;
import com.nexarag.chat.cache.ConversationContextLock;
import com.nexarag.chat.entity.ChatConversation;
import com.nexarag.chat.entity.ChatMessage;
import com.nexarag.chat.enums.ChatMessageRole;
import com.nexarag.chat.enums.ChatMessageStatus;
import com.nexarag.chat.enums.ConversationStatus;
import com.nexarag.chat.id.ChatIdGenerator;
import com.nexarag.chat.mapper.ChatConversationMapper;
import com.nexarag.chat.mapper.ChatMessageMapper;
import com.nexarag.chat.service.ConversationService;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.nexarag.chat.constants.ConversationQueryConstants.MAX_CONVERSATION_PAGE_SIZE;

/**
 * 会话生命周期服务，负责会话创建、查询、改名、归档和删除。
 */
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl extends ServiceImpl<ChatConversationMapper, ChatConversation>
        implements ConversationService {

    private static final String DEFAULT_TITLE = "新会话";

    private final ChatIdGenerator chatIdGenerator;
    private final ChatMessageMapper chatMessageMapper;
    private final ConversationContextLock contextLock;
    private final ConversationContextCache contextCache;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatConversationVO create(String userId, String title) {
        validateUserId(userId);
        ChatConversation entity = ChatConversation.builder()
                .conversationId(chatIdGenerator.nextId())
                .userId(userId)
                .title(title == null || title.isBlank() ? DEFAULT_TITLE : title.trim())
                .status(ConversationStatus.ACTIVE.name())
                .version(0)
                .build();
        save(entity);
        return toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatConversationVO getOwned(String conversationId, String userId) {
        return lambdaQuery()
                .eq(ChatConversation::getConversationId, conversationId)
                .eq(ChatConversation::getUserId, userId)
                .ne(ChatConversation::getStatus, ConversationStatus.DELETED.name())
                .oneOpt()
                .map(this::toDomain)
                .orElseThrow(() -> new ClientException("会话不存在或不属于当前用户"));
    }

    @Override
    @Transactional(readOnly = true)
    public IPage<ChatConversationVO> pageByUser(String userId, long current, long size) {
        validateUserId(userId);
        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), MAX_CONVERSATION_PAGE_SIZE);
        IPage<ChatConversation> entityPage = baseMapper.selectPage(Page.of(safeCurrent, safeSize),
                new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getUserId, userId)
                        .ne(ChatConversation::getStatus, ConversationStatus.DELETED.name())
                        .orderByDesc(ChatConversation::getUpdateTime));
        Page<ChatConversationVO> page = Page.of(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        page.setRecords(entityPage.getRecords().stream().map(this::toDomain).toList());
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(String conversationId, String userId, String title) {
        ChatConversation entity = getOwnedConversation(conversationId, userId);
        if (title == null || title.isBlank()) {
            throw new ClientException("会话标题不能为空");
        }
        entity.setTitle(title.trim());
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(String conversationId, String userId) {
        updateStatus(conversationId, userId, ConversationStatus.ARCHIVED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String conversationId, String userId) {
        contextLock.execute(userId, conversationId, () -> {
            // 1. 校验会话归属并阻止活动生成与删除并发
            ChatConversation entity = getOwnedConversation(conversationId, userId);
            if (hasGeneratingAssistantMessage(conversationId, userId)) {
                throw new ClientException("当前会话正在生成回答，暂不允许删除");
            }

            // 2. 级联逻辑删除会话和其全部消息
            entity.setStatus(ConversationStatus.DELETED.name());
            if (!updateById(entity) || !removeById(conversationId)) {
                throw new ClientException("删除会话失败，请重试");
            }
            chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getConversationId, conversationId)
                    .eq(ChatMessage::getUserId, userId));

            // 3. 提交成功后清除活跃上下文缓存
            evictContextAfterCommit(userId, conversationId);
        });
    }

    /**
     * 查询属于指定用户的会话实体，供同一模块的消息服务复用。
     */
    public ChatConversation getOwnedConversation(String conversationId, String userId) {
        return lambdaQuery()
                .eq(ChatConversation::getConversationId, conversationId)
                .eq(ChatConversation::getUserId, userId)
                .ne(ChatConversation::getStatus, ConversationStatus.DELETED.name())
                .oneOpt()
                .orElseThrow(() -> new ClientException("会话不存在或不属于当前用户"));
    }

    private void updateStatus(String conversationId, String userId, ConversationStatus status) {
        ChatConversation entity = getOwnedConversation(conversationId, userId);
        entity.setStatus(status.name());
        updateById(entity);
    }

    private boolean hasGeneratingAssistantMessage(String conversationId, String userId) {
        return chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getRole, ChatMessageRole.ASSISTANT.name())
                .eq(ChatMessage::getStatus, ChatMessageStatus.GENERATING.name())) > 0;
    }

    private void evictContextAfterCommit(String userId, String conversationId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            contextCache.evict(userId, conversationId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                contextCache.evict(userId, conversationId);
            }
        });
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ClientException("用户 ID 不能为空");
        }
    }

    private ChatConversationVO toDomain(ChatConversation entity) {
        ChatConversationVO vo = BeanUtil.copyProperties(entity, ChatConversationVO.class);
        vo.setStatus(ConversationStatus.valueOf(entity.getStatus()));
        return vo;
    }
}
