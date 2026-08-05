package com.nexarag.model.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.model.dto.prompt.PromptPreviewRequest;
import com.nexarag.model.dto.prompt.PromptPreviewResponse;
import com.nexarag.model.dto.prompt.PromptReleaseRequest;
import com.nexarag.model.dto.prompt.PromptReleaseResponse;
import com.nexarag.model.dto.prompt.PromptResponse;
import com.nexarag.model.dto.prompt.PromptRollbackRequest;
import com.nexarag.model.dto.prompt.PromptSubmitRequest;
import com.nexarag.model.dto.prompt.PromptUpdateDTO;
import com.nexarag.model.prompt.domain.PromptCanaryRule;
import com.nexarag.model.service.PromptManagementService;
import com.nexarag.model.prompt.PromptOperatorProvider;
import com.nexarag.model.service.PromptPublishService;
import com.nexarag.model.prompt.domain.PromptReleaseResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Prompt 在线查询、预览、提交、发布与回滚 REST 接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model/prompts")
public class PromptController {

    private final PromptManagementService promptManagementService;
    private final PromptPublishService promptPublishService;
    private final PromptOperatorProvider promptOperatorProvider;

    /**
     * 查询 Prompt 定义列表。
     *
     * @return Prompt 定义摘要列表
     */
    @GetMapping
    public Result<List<PromptResponse>> listPrompts() {
        // 1. 查询 Prompt 定义摘要列表
        return Results.success(promptManagementService.listPrompts());
    }

    /**
     * 查询 Prompt 详情及完整历史。
     *
     * @param promptCode Prompt 编码
     * @return Prompt 详情
     */
    @GetMapping("/{promptCode}")
    public Result<PromptResponse> getPrompt(@PathVariable String promptCode) {
        // 1. 查询 Prompt 定义、版本和发布历史
        return Results.success(promptManagementService.getPrompt(promptCode));
    }

    /**
     * 更新 Prompt 基础定义（名称、变量契约与启用状态）。
     *
     * @param promptCode Prompt 编码
     * @param request 更新请求
     * @return 更新后的 Prompt 详情
     */
    @PutMapping("/{promptCode}")
    public Result<PromptResponse> updatePrompt(@PathVariable String promptCode,
                                               @Valid @RequestBody PromptUpdateDTO request) {
        return Results.success(promptManagementService.updatePrompt(promptCode, request));
    }

    /**
     * 使用脱敏示例变量预览 Prompt 正文。
     *
     * @param promptCode Prompt 编码
     * @param request 预览请求
     * @return 脱敏渲染正文
     */
    @PostMapping("/{promptCode}/preview")
    public Result<PromptPreviewResponse> preview(@PathVariable String promptCode,
                                                 @Valid @RequestBody PromptPreviewRequest request) {
        // 1. 仅委托本地脱敏渲染服务，不触发版本写入和模型调用
        String content = promptManagementService.preview(promptCode, request.content(), Map.of());
        return Results.success(PromptPreviewResponse.builder().content(content).build());
    }

    /**
     * 提交新正文并立即发布为正式版本。
     *
     * @param promptCode Prompt 编码
     * @param request 提交请求
     * @return 发布结果
     */
    @PostMapping("/{promptCode}/submit")
    public Result<PromptReleaseResponse> submit(@PathVariable String promptCode,
                                                @Valid @RequestBody PromptSubmitRequest request) {
        // 1. 从当前请求上下文读取操作人
        String operator = promptOperatorProvider.getCurrentOperator();

        // 2. 提交新正文并立即创建正式发布记录
        return Results.success(toReleaseResponse(promptPublishService.submit(promptCode, request.content(), operator)));
    }

    /**
     * 发布已有正式版本和可选灰度版本。
     *
     * @param promptCode Prompt 编码
     * @param request 发布请求
     * @return 发布结果
     */
    @PostMapping("/{promptCode}/release")
    public Result<PromptReleaseResponse> release(@PathVariable String promptCode,
                                                 @Valid @RequestBody PromptReleaseRequest request) {
        // 1. 从当前请求上下文读取操作人并构造已校验范围的灰度规则
        String operator = promptOperatorProvider.getCurrentOperator();
        PromptCanaryRule canaryRule = request.canaryPercentage() == null ? null
                : new PromptCanaryRule(request.canaryPercentage());

        // 2. 委托发布服务校验版本归属并追加发布记录
        return Results.success(toReleaseResponse(promptPublishService.release(promptCode, request.stableVersionId(),
                request.canaryVersionId(), canaryRule, operator)));
    }

    /**
     * 回滚到当前 Prompt 的历史版本。
     *
     * @param promptCode Prompt 编码
     * @param request 回滚请求
     * @return 回滚发布结果
     */
    @PostMapping("/{promptCode}/rollback")
    public Result<PromptReleaseResponse> rollback(@PathVariable String promptCode,
                                                  @Valid @RequestBody PromptRollbackRequest request) {
        // 1. 从当前请求上下文读取操作人
        String operator = promptOperatorProvider.getCurrentOperator();

        // 2. 委托服务校验目标版本归属并追加回滚发布记录
        return Results.success(toReleaseResponse(promptPublishService.rollback(promptCode, request.targetVersionId(), operator)));
    }

    private PromptReleaseResponse toReleaseResponse(PromptReleaseResult result) {
        return PromptReleaseResponse.builder()
                .versionId(result.versionId()).releaseId(result.releaseId()).releaseRevision(result.releaseRevision())
                .build();
    }
}
