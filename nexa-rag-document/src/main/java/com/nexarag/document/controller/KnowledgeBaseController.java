package com.nexarag.document.controller;

import com.nexarag.common.web.PageVO;
import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.document.model.dto.CreateKnowledgeBaseDTO;
import com.nexarag.document.model.dto.UpdateKnowledgeBaseDTO;
import com.nexarag.document.model.vo.KnowledgeBaseDetailVO;
import com.nexarag.document.model.vo.KnowledgeBaseSummaryVO;
import com.nexarag.document.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库管理接口控制器，提供租户范围内的创建、查询、更新与删除能力。
 */
@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /** 创建知识库。 */
    @PostMapping
    public Result<KnowledgeBaseDetailVO> create(@Valid @RequestBody CreateKnowledgeBaseDTO request) {
        return Results.success(knowledgeBaseService.create(request));
    }

    /** 分页查询知识库。 */
    @GetMapping
    public Result<PageVO<KnowledgeBaseSummaryVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Results.success(knowledgeBaseService.pageKnowledgeBases(pageNum, pageSize));
    }

    /** 查询知识库详情。 */
    @GetMapping("/{knowledgeBaseId}")
    public Result<KnowledgeBaseDetailVO> getDetail(@PathVariable Long knowledgeBaseId) {
        return Results.success(knowledgeBaseService.getDetail(knowledgeBaseId));
    }

    /** 更新知识库名称和描述。 */
    @PutMapping("/{knowledgeBaseId}")
    public Result<KnowledgeBaseDetailVO> update(@PathVariable Long knowledgeBaseId,
                                                 @Valid @RequestBody UpdateKnowledgeBaseDTO request) {
        return Results.success(knowledgeBaseService.update(knowledgeBaseId, request));
    }

    /** 删除空的非默认知识库。 */
    @DeleteMapping("/{knowledgeBaseId}")
    public Result<Void> delete(@PathVariable Long knowledgeBaseId) {
        knowledgeBaseService.delete(knowledgeBaseId);
        return Results.success();
    }
}
