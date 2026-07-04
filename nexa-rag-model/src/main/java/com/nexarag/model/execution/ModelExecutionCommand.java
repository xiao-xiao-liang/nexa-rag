package com.nexarag.model.execution;

import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelRequestType;
import com.nexarag.model.route.ModelRouteDecision;

import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * 模型执行命令。
 *
 * @param traceId                  链路追踪ID
 * @param bizType                  业务类型
 * @param bizId                    业务ID
 * @param routeKey                 路由Key
 * @param requestType              请求类型
 * @param executor                 实际模型调用逻辑
 * @param promptTokenExtractor     输入Token提取器
 * @param completionTokenExtractor 输出Token提取器
 * @param totalTokenExtractor      总Token提取器
 * @param <T>                      模型响应类型
 */
public record ModelExecutionCommand<T>(
        String traceId,
        ModelBizType bizType,
        String bizId,
        String routeKey,
        ModelRequestType requestType,
        Function<ModelRouteDecision, T> executor,
        ToIntFunction<T> promptTokenExtractor,
        ToIntFunction<T> completionTokenExtractor,
        ToIntFunction<T> totalTokenExtractor) {
}
