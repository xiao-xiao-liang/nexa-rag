package com.nexarag.chat.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexarag.auth.context.CurrentUserContext;
import com.nexarag.chat.domain.*;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.chat.service.ConversationService;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.web.CursorPageVO;
import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import static com.nexarag.chat.constants.ConversationQueryConstants.*;

/**
 * 会话控制器，负责管理当前用户的会话生命周期（增删改查）与历史消息读取。
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationMessageService messageService;

    /**
     * 创建新会话。
     *
     * @param request 创建会话请求
     * @return 新建会话数据
     */
    @PostMapping
    public Result<ConversationListItemVO> create(@RequestBody(required = false) @Valid CreateConversationRequest request) {
        String userId = CurrentUserContext.getRequired().userId();
        String title = Optional.ofNullable(request).map(CreateConversationRequest::title).orElse(null);
        ChatConversationVO conversation = conversationService.create(userId, title);
        return Results.success(toConversationItem(conversation));
    }

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
     * 获取指定会话详情。
     *
     * @param conversationId 会话 ID
     * @return 会话详情
     */
    @GetMapping("/{conversationId}")
    public Result<ConversationListItemVO> get(@PathVariable String conversationId) {
        String userId = CurrentUserContext.getRequired().userId();
        ChatConversationVO conversation = conversationService.getOwned(conversationId, userId);
        return Results.success(toConversationItem(conversation));
    }

    /**
     * 修改指定会话标题。
     *
     * @param conversationId 会话 ID
     * @param request 修改请求
     * @return 操作结果
     */
    @PutMapping("/{conversationId}")
    public Result<Void> update(@PathVariable String conversationId,
                               @RequestBody @Valid UpdateConversationRequest request) {
        String userId = CurrentUserContext.getRequired().userId();
        conversationService.rename(conversationId, userId, request.title());
        return Results.success();
    }

    /**
     * 删除指定会话。
     *
     * @param conversationId 会话 ID
     * @return 操作结果
     */
    @DeleteMapping("/{conversationId}")
    public Result<Void> delete(@PathVariable String conversationId) {
        String userId = CurrentUserContext.getRequired().userId();
        conversationService.delete(conversationId, userId);
        return Results.success();
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
