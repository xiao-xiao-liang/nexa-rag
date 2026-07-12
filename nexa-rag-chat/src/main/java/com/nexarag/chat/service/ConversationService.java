package com.nexarag.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexarag.chat.domain.ChatConversationVO;
import com.nexarag.chat.entity.ChatConversation;


/**
 * 管理聊天会话生命周期。
 */
public interface ConversationService extends IService<ChatConversation> {

    /**
     * 查询用户拥有的会话实体。
     *
     * @param conversationId 会话 ID
     * @param userId 用户 ID
     * @return 会话实体
     */
    ChatConversation getOwnedConversation(String conversationId, String userId);

    /**
     * 创建会话。
     *
     * @param userId 用户 ID
     * @param title 会话标题
     * @return 会话领域对象
     */
    ChatConversationVO create(String userId, String title);

    /**
     * 查询用户拥有的会话。
     *
     * @param conversationId 会话 ID
     * @param userId 用户 ID
     * @return 会话领域对象
     */
    ChatConversationVO getOwned(String conversationId, String userId);

    /**
     * 分页查询用户拥有的会话。
     *
     * @param userId 用户 ID
     * @param current 当前页码
     * @param size 每页大小
     * @return 会话分页结果
     */
    IPage<ChatConversationVO> pageByUser(String userId, long current, long size);

    /**
     * 修改会话标题。
     *
     * @param conversationId 会话 ID
     * @param userId 用户 ID
     * @param title 新标题
     */
    void rename(String conversationId, String userId, String title);

    /**
     * 归档会话。
     *
     * @param conversationId 会话 ID
     * @param userId 用户 ID
     */
    void archive(String conversationId, String userId);

    /**
     * 删除会话。
     *
     * @param conversationId 会话 ID
     * @param userId 用户 ID
     */
    void delete(String conversationId, String userId);
}
