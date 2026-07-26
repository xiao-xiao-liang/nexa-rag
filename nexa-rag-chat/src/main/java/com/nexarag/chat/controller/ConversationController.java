package com.nexarag.chat.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexarag.auth.context.CurrentUserContext;
import com.nexarag.chat.controller.vo.ConversationHistoryPageVO;
import com.nexarag.chat.controller.vo.ConversationListItemVO;
import com.nexarag.chat.controller.vo.ConversationMessageItemVO;
import com.nexarag.chat.controller.vo.ConversationPageVO;
import com.nexarag.chat.domain.ChatConversationVO;
import com.nexarag.chat.domain.ChatMessageVO;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.chat.service.ConversationService;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.web.CursorPageVO;
import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.nexarag.chat.constants.ConversationQueryConstants.MAX_CONVERSATION_PAGE_CURRENT;
import static com.nexarag.chat.constants.ConversationQueryConstants.MAX_CONVERSATION_PAGE_SIZE;
import static com.nexarag.chat.constants.ConversationQueryConstants.MAX_HISTORY_PAGE_SIZE;

/**
 * 会话读取控制器，负责返回当前用户的会话列表和历史消息安全投影。
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationMessageService messageService;

    /**
     * 分页查询当前用户的会话列表。
     *
     * @param current 当前页码
     * @param size 每页数量
     * @return 会话列表分页结果
     */
    @GetMapping
    public Result<ConversationPageVO> list(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "20") long size) {
        // 1. 校验分页参数，避免深分页和服务层静默归一化
        validateConversationPageParameters(current, size);

        // 2. 从鉴权上下文获取当前用户
        String userId = CurrentUserContext.getRequired().userId();

        // 3. 查询并转换为不含内部字段的会话投影
        IPage<ChatConversationVO> page = conversationService.pageByUser(userId, current, size);
        ConversationPageVO response = new ConversationPageVO();
        response.setRecords(page.getRecords().stream().map(this::toConversationItem).toList());
        response.setTotal(page.getTotal());
        response.setCurrent(page.getCurrent());
        response.setSize(page.getSize());
        response.setPages(page.getPages());
        return Results.success(response);
    }

    /**
     * 使用消息序号游标查询当前用户的会话历史。
     *
     * @param conversationId 会话 ID
     * @param beforeSequence 仅查询该序号之前的消息；为空时查询最新消息
     * @param size 本次查询数量
     * @return 会话历史游标分页结果
     */
    @GetMapping("/{conversationId}/messages")
    public Result<ConversationHistoryPageVO> history(@PathVariable String conversationId,
                                                      @RequestParam(required = false) Long beforeSequence,
                                                      @RequestParam(defaultValue = "50") int size) {
        // 1. 校验游标和分页参数，避免服务层静默归一化
        validateHistoryPageParameters(beforeSequence, size);

        // 2. 从鉴权上下文获取当前用户
        String userId = CurrentUserContext.getRequired().userId();

        // 3. 查询并转换为不含内部字段的历史消息投影
        CursorPageVO<ChatMessageVO> page = messageService.pageHistory(conversationId, userId, beforeSequence, size);
        ConversationHistoryPageVO response = new ConversationHistoryPageVO();
        response.setRecords(page.getRecords().stream().map(this::toMessageItem).toList());
        response.setHasMore(page.isHasMore());
        response.setNextBeforeSequence(page.getNextBeforeSequence());
        return Results.success(response);
    }

    /**
     * 转换会话领域对象为外部安全投影。
     *
     * @param conversation 会话领域对象
     * @return 会话列表项
     */
    private ConversationListItemVO toConversationItem(ChatConversationVO conversation) {
        return ConversationListItemVO.builder()
                .conversationId(conversation.getConversationId())
                .title(conversation.getTitle())
                .status(conversation.getStatus())
                .lastMessageTime(conversation.getLastMessageTime())
                .createdTime(conversation.getCreatedTime())
                .updatedTime(conversation.getUpdatedTime())
                .build();
    }

    /**
     * 转换完整消息领域对象为外部安全投影。
     *
     * @param message 消息领域对象
     * @return 历史消息项
     */
    private ConversationMessageItemVO toMessageItem(ChatMessageVO message) {
        return ConversationMessageItemVO.builder()
                .messageId(message.messageId())
                .sequence(message.sequence())
                .role(message.role())
                .status(message.status())
                .content(message.content())
                .createdTime(message.createdTime())
                .updatedTime(message.updatedTime())
                .build();
    }

    /**
     * 校验会话列表分页参数。
     *
     * @param current 当前页码
     * @param size 每页数量
     */
    private void validateConversationPageParameters(long current, long size) {
        if (current < 1 || current > MAX_CONVERSATION_PAGE_CURRENT) {
            throw new ClientException("当前页码必须在 1 到 " + MAX_CONVERSATION_PAGE_CURRENT + " 之间",
                    BaseErrorCode.PARAM_ERROR);
        }
        if (size < 1 || size > MAX_CONVERSATION_PAGE_SIZE) {
            throw new ClientException("会话列表每页数量必须在 1 到 " + MAX_CONVERSATION_PAGE_SIZE + " 之间",
                    BaseErrorCode.PARAM_ERROR);
        }
    }

    /**
     * 校验历史消息游标分页参数。
     *
     * @param beforeSequence 历史消息游标
     * @param size 每页数量
     */
    private void validateHistoryPageParameters(Long beforeSequence, int size) {
        if (beforeSequence != null && beforeSequence <= 0) {
            throw new ClientException("历史消息游标必须大于 0", BaseErrorCode.PARAM_ERROR);
        }
        if (size < 1 || size > MAX_HISTORY_PAGE_SIZE) {
            throw new ClientException("历史消息每页数量必须在 1 到 " + MAX_HISTORY_PAGE_SIZE + " 之间",
                    BaseErrorCode.PARAM_ERROR);
        }
    }
}
