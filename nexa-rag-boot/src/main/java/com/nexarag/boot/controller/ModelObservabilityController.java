package com.nexarag.boot.controller;

import static com.nexarag.boot.constants.ModelObservabilityApiPathConstant.OVERVIEW;
import static com.nexarag.boot.constants.ModelObservabilityApiPathConstant.ROOT;
import static com.nexarag.boot.constants.ModelObservabilityApiPathConstant.TOKEN_DISTRIBUTION;
import static com.nexarag.boot.constants.ModelObservabilityApiPathConstant.TRACES;

import com.nexarag.boot.observability.ModelObservabilityOverviewVO;
import com.nexarag.boot.observability.ModelObservabilityQuery;
import com.nexarag.boot.observability.ModelObservabilityService;
import com.nexarag.boot.observability.ModelTokenDistributionVO;
import com.nexarag.boot.observability.ModelObservabilityTraceVO;
import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型观测管理接口，仅返回不含提示词正文的聚合指标。
 */
@RestController
@RequestMapping(ROOT)
@RequiredArgsConstructor
public class ModelObservabilityController {

    private final ModelObservabilityService modelObservabilityService;

    /**
     * 查询 Token 与首 Token 时延概览。
     *
     * @param query 时间范围和可选筛选条件
     * @return 管理端安全展示的模型观测概览
     */
    @GetMapping(OVERVIEW)
    public Result<ModelObservabilityOverviewVO> overview(@ModelAttribute ModelObservabilityQuery query) {
        return Results.success(modelObservabilityService.overview(query));
    }

    /**
     * 查询完整 RAG 分段的 Token 分布。
     *
     * @param query 时间范围和可选筛选条件
     * @return 语义分段的安全统计数据
     */
    @GetMapping(TOKEN_DISTRIBUTION)
    public Result<ModelTokenDistributionVO> tokenDistribution(@ModelAttribute ModelObservabilityQuery query) {
        return Results.success(modelObservabilityService.tokenDistribution(query));
    }

    /**
     * 查询安全的模型调用明细。
     *
     * @param query 时间范围和可选筛选条件
     * @return 不含任何正文或身份信息的明细列表
     */
    @GetMapping(TRACES)
    public Result<List<ModelObservabilityTraceVO>> traces(@ModelAttribute ModelObservabilityQuery query) {
        return Results.success(modelObservabilityService.traces(query));
    }
}
