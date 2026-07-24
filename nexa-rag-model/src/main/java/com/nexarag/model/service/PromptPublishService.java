package com.nexarag.model.service;

import com.nexarag.model.prompt.domain.PromptCanaryRule;
import com.nexarag.model.prompt.domain.PromptReleaseResult;

/**
 * Prompt 编辑、发布和回滚服务。
 */
public interface PromptPublishService {

    /**
     * 提交新正文并立即发布为正式版本。
     *
     * @param promptCode Prompt 编码
     * @param content    模板正文
     * @param operator   操作人
     * @return 发布结果
     */
    PromptReleaseResult submit(String promptCode, String content, String operator);

    /**
     * 发布指定正式和灰度版本。
     *
     * @param promptCode      Prompt 编码
     * @param stableVersionId 正式版本ID
     * @param canaryVersionId 灰度版本ID
     * @param canaryRule      灰度规则
     * @param operator        操作人
     * @return 发布结果
     */
    PromptReleaseResult release(String promptCode, Long stableVersionId, Long canaryVersionId,
                                PromptCanaryRule canaryRule, String operator);

    /**
     * 回滚到历史版本，并新增一条发布记录。
     *
     * @param promptCode      Prompt 编码
     * @param targetVersionId 目标版本ID
     * @param operator        操作人
     * @return 发布结果
     */
    PromptReleaseResult rollback(String promptCode, Long targetVersionId, String operator);
}
