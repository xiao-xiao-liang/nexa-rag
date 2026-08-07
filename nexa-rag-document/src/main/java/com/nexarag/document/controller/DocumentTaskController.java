package com.nexarag.document.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.document.model.vo.DocumentTaskVO;
import com.nexarag.document.service.DocumentTaskAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档异步任务管理接口，提供状态查询与人工重试。
 */
@RestController
@RequestMapping("/api/document-tasks")
@RequiredArgsConstructor
public class DocumentTaskController {

    private final DocumentTaskAdminService taskAdminService;

    /**
     * 查询单个文档异步任务状态。
     *
     * @param outboxId 任务Outbox ID
     * @return 脱敏任务详情
     */
    @GetMapping("/{outboxId}")
    public Result<DocumentTaskVO> getTask(@PathVariable Long outboxId) {
        return Results.success(taskAdminService.getTask(outboxId));
    }

    /**
     * 人工重试最终失败的文档异步任务。
     *
     * @param outboxId 失败任务Outbox ID
     * @return 新建任务详情
     */
    @PostMapping("/{outboxId}/retry")
    public Result<DocumentTaskVO> retryTask(@PathVariable Long outboxId) {
        return Results.success(taskAdminService.retryFailedTask(outboxId));
    }
}
